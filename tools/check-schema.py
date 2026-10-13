#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
建表脚本静态检查器（阶段 1 配套工具）

用途：写完 sql/schema.sql 后先自己跑一遍，按《01-阶段1-建表规格》的检查清单自检。
它只做静态文本分析，不连数据库，所以可以随时跑。

用法：
    python tools/check-schema.py sql/schema.sql
    python tools/check-schema.py sql/schema.sql --expect user,product,trade_order

退出码：0 = 无 ERROR；1 = 存在 ERROR（WARN 不影响退出码）
"""

import os
import re
import sys

# ---------- 期望的表（阶段 1 规格） ----------
EXPECTED_TABLES = [
    "user", "category", "product", "favorite", "bargain", "trade_order",
    "delivery_order", "review", "complaint", "flash_sale_record", "message",
]

# 状态/角色/类型类字段应使用 tinyint
TINYINT_HINTS = ("status", "role", "type", "is_", "star", "level", "flag")

# 金额类字段应使用 decimal(10,2)
MONEY_HINTS = ("price", "offer", "fee", "amount", "money", "cost", "balance")

# 长文本字段建议用 text
LONGTEXT_HINTS = ("description", "content", "remark", "note", "reason")

AUDIT = ("create_time", "update_time")


# ---------- MySQL 8.0 保留字（数据来自 tools/mysql-reserved-words.txt） ----------
# 为什么单独校验它：保留字**可以建表成功**，但写 SQL 查询时若忘加反引号会报
# ERROR 1064 语法错误 —— 属于"建表过、查询炸"的隐形坑，实测确认（condition 列）。
def _load_reserved_words():
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "mysql-reserved-words.txt")
    if not os.path.exists(path):
        return frozenset()
    words = set()
    # utf-8-sig：兼容带 BOM 的文件（Windows 记事本 / PowerShell 5.1 的 Set-Content -Encoding UTF8
    # 都会写 BOM，实测会把第一行注释污染成一个假"保留字"）
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            # 只接受合法标识符，挡住 BOM 残留、空行、说明文字等垃圾条目
            if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", s):
                words.add(s.upper())
    return frozenset(words)


RESERVED_WORDS = _load_reserved_words()

TABLE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?\s*\(",
    re.I,
)
COL_RE = re.compile(
    r"^\s*`?([A-Za-z_][A-Za-z0-9_]*)`?\s+([A-Za-z]+)\s*(?:\(([^)]*)\))?\s*(.*)$"
)
IDX_RE = re.compile(
    r"^\s*(?:UNIQUE\s+)?(?:KEY|INDEX)\s+`?([A-Za-z_][A-Za-z0-9_]*)`?\s*\(([^)]*)\)",
    re.I,
)
FK_RE = re.compile(r"FOREIGN\s+KEY", re.I)


def strip_comments(sql):
    out = []
    for line in sql.splitlines():
        s = line.strip()
        if s.startswith("--") or s.startswith("#"):
            continue
        out.append(line)
    sql = "\n".join(out)
    return re.sub(r"/\*.*?\*/", "", sql, flags=re.S)


def split_tables(sql):
    """返回 [(表名, 表体, 表尾属性, 原始起始位置)]"""
    tables = []
    for m in TABLE_RE.finditer(sql):
        name = m.group(1)
        start = m.end() - 1  # 指向表体左括号
        depth, j = 0, start
        while j < len(sql):
            if sql[j] == "(":
                depth += 1
            elif sql[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = sql[start + 1:j]
        tail = sql[j + 1: j + 500]
        tail = tail.split(";")[0]
        tables.append((name, body, tail))
    return tables


def parse_columns(body):
    """返回 [(字段名, 类型, 长度, 行尾属性)]，跳过索引/约束定义行"""
    cols = []
    for line in body.splitlines():
        s = line.strip()
        if not s or s.startswith("--"):
            continue
        head = s.split()[0].upper().strip("`")
        if head in ("PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT",
                    "FULLTEXT", "SPATIAL", "CHECK", "FOREIGN"):
            continue
        m = COL_RE.match(s)
        if m:
            cols.append((m.group(1), m.group(2).lower(), (m.group(3) or "").lower(), m.group(4)))
    return cols


def parse_indexes(body):
    """返回 [(索引名, [列名...], 是否唯一)]"""
    idx = []
    for line in body.splitlines():
        s = line.strip()
        mu = re.match(r"^UNIQUE\s+(?:KEY|INDEX)\s+`?([A-Za-z_][A-Za-z0-9_]*)`?\s*\(([^)]*)\)", s, re.I)
        if mu:
            cols = [c.strip().strip("`").split("(")[0].strip() for c in mu.group(2).split(",")]
            idx.append((mu.group(1), cols, True))
            continue
        m = IDX_RE.match(s)
        if m:
            cols = [c.strip().strip("`").split("(")[0].strip() for c in m.group(2).split(",")]
            idx.append((m.group(1), cols, False))
    return idx


def check(schema_path, expected):
    with open(schema_path, encoding="utf-8") as f:
        raw = f.read()
    sql = strip_comments(raw)
    tables = split_tables(sql)

    errors, warns, oks = [], [], []
    found = [t[0] for t in tables]

    # 1. 表是否齐全
    for t in expected:
        if t in found:
            oks.append("表存在：%s" % t)
        else:
            errors.append("缺少表：%s" % t)
    for t in found:
        if t not in expected:
            warns.append("多出的表：%s（规格外，确认是否有意为之）" % t)

    for name, body, tail in tables:
        cols = parse_columns(body)
        colmap = {c[0]: c for c in cols}
        idx = parse_indexes(body)

        # 2. id 主键
        if "id" not in colmap:
            errors.append("[%s] 缺少 id 字段" % name)
        else:
            t_id, len_id, attr = colmap["id"][1], colmap["id"][2], colmap["id"][3]
            if t_id != "bigint":
                errors.append("[%s] id 类型是 %s，应为 bigint" % (name, t_id))
            if "auto_increment" not in attr.lower():
                warns.append("[%s] id 未声明 AUTO_INCREMENT" % name)
            if not re.search(r"PRIMARY\s+KEY\s*\(\s*`?id`?\s*\)", body, re.I):
                errors.append("[%s] 未声明 PRIMARY KEY (id)" % name)

        # 3. 审计字段
        for a in AUDIT:
            if a not in colmap:
                errors.append("[%s] 缺少审计字段 %s" % (name, a))
            elif colmap[a][1] not in ("datetime", "timestamp"):
                errors.append("[%s] %s 类型是 %s，应为 datetime" % (name, a, colmap[a][1]))

        # 3.5 保留字（建表能过、查询报 1064 的隐形坑）
        if RESERVED_WORDS and name.upper() in RESERVED_WORDS:
            warns.append("[%s] 表名是 MySQL 保留字，建议换名" % name)
        for cname, ctype, clen, cattr in cols:
            if RESERVED_WORDS and cname.upper() in RESERVED_WORDS:
                errors.append("[%s] 字段名 %s 是 MySQL 保留字：建表会成功，但查询时忘加反引号会报 "
                              "ERROR 1064。建议换名（例：condition → condition_level）" % (name, cname))

        # 4. 关联 ID 必须 bigint
        for cname, ctype, clen, cattr in cols:
            if cname.endswith("_id") and cname != "id":
                if ctype != "bigint":
                    errors.append("[%s] 关联字段 %s 类型是 %s，必须 bigint（禁止 varchar 存 ID）"
                                  % (name, cname, ctype))

        # 5. 金额必须 decimal(10,2)
        for cname, ctype, clen, cattr in cols:
            low = cname.lower()
            if any(h in low for h in MONEY_HINTS) or low == "price":
                if ctype in ("float", "double", "real"):
                    errors.append("[%s] 金额字段 %s 用了 %s，必须 decimal(10,2)" % (name, cname, ctype))
                elif ctype == "decimal" and clen.replace(" ", "") != "10,2":
                    warns.append("[%s] 金额字段 %s 是 decimal(%s)，规格建议 decimal(10,2)" % (name, cname, clen))

        # 6. 状态类字段 tinyint
        for cname, ctype, clen, cattr in cols:
            low = cname.lower()
            if any(h in low for h in TINYINT_HINTS):
                if ctype in ("varchar", "char", "text"):
                    errors.append("[%s] 状态类字段 %s 用了 %s，应使用 tinyint 数字编码"
                                  % (name, cname, ctype))
                elif ctype in ("int", "bigint"):
                    warns.append("[%s] 状态类字段 %s 用了 %s，建议 tinyint" % (name, cname, ctype))

        # 7. 长文本建议 text
        for cname, ctype, clen, cattr in cols:
            if any(h in cname.lower() for h in LONGTEXT_HINTS) and ctype == "varchar":
                try:
                    n = int(clen)
                    if n > 500:
                        warns.append("[%s] %s 是 varchar(%s)，长内容建议用 text" % (name, cname, clen))
                except ValueError:
                    pass

        # 8. 引擎/字符集
        low = tail.lower()
        if "innodb" not in low:
            errors.append("[%s] 未指定 ENGINE=InnoDB" % name)
        if "utf8mb4" not in low:
            errors.append("[%s] 未指定 CHARSET=utf8mb4" % name)
        if "utf8mb4" in low and "collate" not in low:
            warns.append("[%s] 只写 CHARSET 没写 COLLATE：MySQL 8 会默认成 utf8mb4_0900_ai_ci，"
                         "与规格要求的 utf8mb4_unicode_ci 不一致（实测确认）" % name)

        # 9. 物理外键
        if FK_RE.search(body):
            errors.append("[%s] 出现 FOREIGN KEY 物理外键，规格要求不建物理外键" % name)

        # 10. 表注释
        if not re.search(r"COMMENT\s*=", tail, re.I):
            warns.append("[%s] 表缺少 COMMENT" % name)

        # 11. 字段注释
        no_comment = [c[0] for c in cols
                      if c[0] not in ("id",) and "comment" not in c[3].lower()]
        hard = [c for c in no_comment if c not in AUDIT]
        if hard:
            warns.append("[%s] 字段缺少 COMMENT：%s" % (name, ", ".join(hard)))

        # 12. 关联字段是否有索引
        indexed_cols = set()
        for _, icols, _u in idx:
            indexed_cols.update(icols)
        if re.search(r"PRIMARY\s+KEY", body, re.I):
            indexed_cols.add("id")
        for cname, ctype, clen, cattr in cols:
            if cname.endswith("_id") and cname != "id" and cname not in indexed_cols:
                warns.append("[%s] 关联字段 %s 没有索引（关联查询会全表扫描）" % (name, cname))

        # 13. 特定业务约束
        if name == "favorite":
            if not any(u and set(c) >= {"user_id", "product_id"} for _, c, u in idx):
                errors.append("[favorite] 缺少 (user_id, product_id) 联合唯一索引，会重复收藏")
        if name == "trade_order":
            if not any(u and "order_no" in c for _, c, u in idx):
                errors.append("[trade_order] 缺少 order_no 唯一索引")

    # ---------- 输出 ----------
    print("=" * 68)
    print("建表脚本检查：%s" % schema_path)
    print("解析到 %d 张表：%s" % (len(tables), ", ".join(found) if found else "(无)"))
    print("=" * 68)
    if errors:
        print("\n[ERROR] 必须修复（%d）" % len(errors))
        for e in errors:
            print("  x %s" % e)
    if warns:
        print("\n[WARN] 建议确认（%d）" % len(warns))
        for w in warns:
            print("  ! %s" % w)
    print("\n[PASS] 通过项（%d）" % len(oks))
    for o in oks:
        print("  v %s" % o)
    print("\n结论：%s" % ("存在必须修复的问题" if errors else "无 ERROR，可以提交评审"))
    return 1 if errors else 0


def main():
    args = [a for a in sys.argv[1:]]
    expected = EXPECTED_TABLES
    if "--expect" in args:
        i = args.index("--expect")
        expected = [t.strip() for t in args[i + 1].split(",") if t.strip()]
        del args[i:i + 2]
    if not args:
        default = os.path.join("sql", "schema.sql")
        args = [default]
    path = args[0]
    if not os.path.exists(path):
        print("找不到文件：%s" % path)
        return 2
    return check(path, expected)


if __name__ == "__main__":
    sys.exit(main())
