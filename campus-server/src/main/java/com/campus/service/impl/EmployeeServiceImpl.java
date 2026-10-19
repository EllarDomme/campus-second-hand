package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.constant.MessageConstant;
import com.campus.constant.StatusConstant;
import com.campus.context.BaseContext;
import com.campus.dto.EmployeeDTO;
import com.campus.dto.EmployeeLoginDTO;
import com.campus.dto.EmployeePageQueryDTO;
import com.campus.entity.Employee;
import com.campus.exception.AccountLockedException;
import com.campus.exception.AccountNotFoundException;
import com.campus.exception.PasswordErrorException;
import com.campus.mapper.EmployeeMapper;
import com.campus.result.PageResult;
import com.campus.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO 登录参数
     * @return 登录结果
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        // ┌── 补上了骨架里那个 TODO：原来是明文比对 ───────────────────────
        // │ 原版：if (!password.equals(employee.getPassword()))   ← 明文比明文
        // │ 问题：库里存明文，一旦库被拖走所有管理员密码直接泄露；
        // │      而且"肉眼可读"意味着内部人员也能看到别人的密码。
        // │ 改法：库里存 MD5 摘要，登录时把用户输入也摘要一次再比。
        // │ 说明：MD5 不是"加密"而是摘要（不可逆），它防的是"明文泄露"，
        // │      防不住彩虹表 —— 生产环境应该用 BCrypt/Argon2 这类带盐慢哈希。
        // │      这里选 MD5 是因为和原版建表约定一致，改动面最小。
        // └──────────────────────────────────────────────────────────────
        String inputDigest = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        if (!inputDigest.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (StatusConstant.DISABLE.equals(employee.getStatus())) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 添加员工
     * @param employeeDTO 新增参数
     */

    @Override
    public void save(EmployeeDTO employeeDTO) {
        //将DTO转换为Employee实体对象
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态
        employee.setStatus(StatusConstant.ENABLE);

        //设置密码默认值是123456（存摘要，不存明文）
        employee.setPassword(DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8)));

        /**
        //设置当前记录的创建时间和修改时间
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());

        //设置当前创建人和修改人的id
        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setUpdateUser(BaseContext.getCurrentId());
         **/


        //调用Mapper方法，将数据插入到数据库中
        employeeMapper.insert(employee);
    }

    /**
     * 进行分页查询
     *
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        //利用pagehelper进行分页查询
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        //调用mapper方法进行查询
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        //返回分页结果
        long total = page.getTotal();
        List<Employee> list = page.getResult();

        return new PageResult(total,list);
    }

    /**
     * 启动或者禁用员工账号
     * @param status 状态（1：启用，0：禁用）
     * @param id 员工id
     */
    @Override
    public void updateStatus(Integer status, Long id){
        //用employee对象接收状态和id
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
        //调用mapper方法，更新员工账号状态信息
        employeeMapper.update(employee);
    }

    /**
     * 根据id查询回显员工信息
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id){
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("******");
        return employee;
    }

    /**
     * 更新员工信息
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO){
        //进行浅拷贝
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        //调用mapper方法，更新员工信息
        employeeMapper.update(employee);
    }


}
