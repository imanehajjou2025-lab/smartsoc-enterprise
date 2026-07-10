package com.smartsoc.api.identity.user;

import com.smartsoc.api.identity.user.dto.UserDtos.UserResponse;
import com.smartsoc.domain.identity.User;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserApiMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponses(List<User> users);
}
