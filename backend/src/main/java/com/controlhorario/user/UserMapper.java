package com.controlhorario.user;

import java.util.List;

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);

    List<UserDto> toDtos(List<User> users);
}
