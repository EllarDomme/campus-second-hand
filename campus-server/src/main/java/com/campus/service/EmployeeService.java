package com.campus.service;

import com.campus.dto.EmployeeDTO;
import com.campus.dto.EmployeeLoginDTO;
import com.campus.dto.EmployeePageQueryDTO;
import com.campus.entity.Employee;
import com.campus.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    void save(EmployeeDTO employeeDTO);

    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    void updateStatus(Integer status, Long id);

    Employee getById(Long id);

    void update(EmployeeDTO employeeDTO);
}
