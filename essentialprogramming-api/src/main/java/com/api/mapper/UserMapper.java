package com.api.mapper;

import com.api.entities.User;
import com.api.input.EmployeeInput;
import com.api.input.UserInput;
import com.api.output.UserJSON;

import java.util.Arrays;


public class UserMapper {

    public static User inputToUser(UserInput input) {
        return User.builder()
                .firstName(input.getFirstName())
                .lastName(input.getLastName())
                .email(input.getEmail())
                .phone(input.getPhone())
                .roles(Arrays.asList(input.getRoles()))
                .build();
    }

    public static UserJSON userToJson(User user) {
        return UserJSON.builder()
                .email(user.getEmail())
                .userKey(user.getUserKey())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .build();
    }

    public static User employeeToUser(EmployeeInput employeeInput) {
        return User.builder()
                .firstName(employeeInput.getFirstName())
                .lastName(employeeInput.getLastName())
                .email(employeeInput.getEmail())
                .phone(employeeInput.getPhone())
                .build();
    }

}
