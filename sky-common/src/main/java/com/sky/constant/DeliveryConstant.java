package com.sky.constant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 校园跑腿相关常量
 */
public class DeliveryConstant {

    private DeliveryConstant() {
    }

    // ---------- 跑腿单状态（状态机）----------
    //   0 待接单 ──跑腿员抢单──> 1 已接单 ──确认取货──> 2 取货中 ──确认送达──> 3 已送达
    //     任一环节可取消 ──> 4 已取消
    public static final Integer STATUS_WAITING = 0;
    public static final Integer STATUS_ACCEPTED = 1;
    public static final Integer STATUS_PICKED = 2;
    public static final Integer STATUS_FINISHED = 3;
    public static final Integer STATUS_CANCELLED = 4;

    /** 跑腿员角色：user.role = 1 */
    public static final Integer ROLE_RUNNER = 1;
    /** 用户状态正常：user.status = 0 */
    public static final Integer USER_STATUS_NORMAL = 0;

    // ---------- 计价规则 ----------
    // 需求原文：3 公里内 5 元，每超 1 公里加 1 元
    /** 起步价（3 公里内） */
    public static final BigDecimal BASE_FEE = new BigDecimal("5.00");
    /** 起步里程（公里） */
    public static final BigDecimal BASE_KM = new BigDecimal("3.0");
    /** 超出部分每公里加价 */
    public static final BigDecimal FEE_PER_KM = new BigDecimal("1.00");

    // ---------- 距离档位（决策点 6 选了"简化档位"）----------
    // 为什么不用百度地图 API：真实项目应该调地图算距离，但本机受限环境下外网 HTTPS
    // 从中转进程不一定可用（之前踩过）。所以改成"买家选大致档位、服务端按档位算钱"：
    //   · 前端只传档位（1/2/3），【不能传金额】—— 金额永远由服务端算
    //   · 档位→公里数的映射写在服务端，改规则不用改前端
    public static final Integer DISTANCE_LEVEL_NEAR = 1;
    public static final Integer DISTANCE_LEVEL_MID = 2;
    public static final Integer DISTANCE_LEVEL_FAR = 3;

    /**
     * 档位 → 估算公里数
     * 档位为空或非法时按"近"处理（对用户友好，不因为一个可选字段报错）
     */
    public static BigDecimal kmOf(Integer distanceLevel) {
        if (DISTANCE_LEVEL_FAR.equals(distanceLevel)) {
            return new BigDecimal("6.0");
        }
        if (DISTANCE_LEVEL_MID.equals(distanceLevel)) {
            return new BigDecimal("4.0");
        }
        return new BigDecimal("2.0");
    }

    /**
     * 按公里数算跑腿费：3 公里内 5 元，每超 1 公里加 1 元（不足 1 公里按 1 公里算）
     *
     *   2 公里 → 5 元
     *   4 公里 → 5 + 1 = 6 元
     *   6 公里 → 5 + 3 = 8 元
     */
    public static BigDecimal calcFee(BigDecimal km) {
        if (km == null || km.compareTo(BASE_KM) <= 0) {
            return BASE_FEE;
        }
        BigDecimal extraKm = km.subtract(BASE_KM).setScale(0, RoundingMode.CEILING);
        return BASE_FEE.add(extraKm.multiply(FEE_PER_KM)).setScale(2, RoundingMode.HALF_UP);
    }

}
