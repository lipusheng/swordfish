/*
 * Copyright (C) 2017 Baifendian Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baifendian.swordfish.webserver.service;

import com.baifendian.swordfish.common.consts.Constants;
import com.baifendian.swordfish.common.utils.PermUtil;
import com.baifendian.swordfish.dao.enums.UserRoleType;
import com.baifendian.swordfish.dao.mapper.ProjectMapper;
import com.baifendian.swordfish.dao.mapper.ProjectUserMapper;
import com.baifendian.swordfish.dao.mapper.UserMapper;
import com.baifendian.swordfish.dao.model.Project;
import com.baifendian.swordfish.dao.model.ProjectUser;
import com.baifendian.swordfish.dao.model.User;
import com.baifendian.swordfish.webserver.exception.*;
import org.apache.commons.httpclient.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

@Service
public class ProjectService {

  private static Logger logger = LoggerFactory.getLogger(ProjectService.class.getName());

  @Autowired
  private UserMapper userMapper;

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private ProjectUserMapper projectUserMapper;

  /**
   * 创建一个项目
   *
   * @param operator
   * @param name
   * @param desc
   * @return
   */
  public Project createProject(User operator, String name, String desc) {
    // 管理员不能创建项目
    if (operator.getRole() == UserRoleType.ADMIN_USER) {
      throw new PermissionException("admin",operator.getName());
    }

    Project project = new Project();
    Date now = new Date();

    project.setName(name);
    project.setDesc(desc);
    project.setOwnerId(operator.getId());
    project.setOwner(operator.getName());
    project.setCreateTime(now);
    project.setModifyTime(now);

    try {
      projectMapper.insert(project);
    } catch (DuplicateKeyException e) {
      logger.error("Project has exist, can't create again.", e);
      throw new NotModifiedException("Project has exist, can't create again.");
    }

    return project;
  }

  /**
   * 修改项目
   *
   * @param operator
   * @param name
   * @param desc
   * @return
   */
  public Project modifyProject(User operator, String name, String desc) {
    Project project = projectMapper.queryByName(name);

    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 需要是项目的 owner
    if (operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner",operator.getName());
    }

    Date now = new Date();

    if(desc != null) {
      project.setDesc(desc);
    }

    project.setModifyTime(now);

    int count = projectMapper.updateById(project);

    if (count <= 0) {
      throw new NotModifiedException("Not update count");
    }

    return project;
  }

  /**
   * 删除一个项目
   *
   * @param operator
   * @param name
   */
  public void deleteProject(User operator, String name) {
    Project project = projectMapper.queryByName(name);

    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 只有 管理员或 owner 能够删除
    if (operator.getRole() != UserRoleType.ADMIN_USER && operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner or admin",operator.getName());
    }

    // TODO:: 需要判断项目下, 是否有 "工作流/资源/数据源" 存在

    // TODO:: 严格来说, 应该清理 Local/HDFS 上的相关目录

    int count = projectMapper.deleteById(project.getId());

    if (count <= 0) {
      throw new NotModifiedException("Not delete count");
    }

    return;
  }

  /**
   * 查询项目列表
   *
   * @param operator
   * @return
   */
  public List<Project> queryProject(User operator) {
    switch (operator.getRole()) {
      case ADMIN_USER:
        return projectMapper.queryAllProject();
      case GENERAL_USER:
        return projectMapper.queryProjectByUser(operator.getId());
      default:
        return null;
    }
  }

  /**
   * 把一个用户添加到项目中
   *
   * @param operator
   * @param name
   * @param userName
   * @param perm
   * @return
   */
  public ProjectUser addProjectUser(User operator, String name, String userName, int perm) {
    Project project = projectMapper.queryByName(name);

    // 不存在的项目名
    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 操作用户不是项目的 owner
    if (operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner",operator.getName());
    }

    // 查询用户
    User user = userMapper.queryByName(userName);

    // 增加的用户不存在
    if (user == null) {
      throw new NotFoundException("user",name);
    }

    // 不能增加自己
    if (operator.getId() == user.getId()) {
      throw new ParameterException("name");
    }

    ProjectUser projectUser = projectUserMapper.query(project.getId(), user.getId());

    // 增加用户已经存在
    if (projectUser != null) {
      throw new NotModifiedException("User has exist, can't add again.");
    }

    // 构建信息, 插入
    projectUser = new ProjectUser();
    Date now = new Date();

    projectUser.setProjectId(project.getId());
    projectUser.setProjectName(project.getName());
    projectUser.setUserId(user.getId());
    projectUser.setUserName(userName);
    projectUser.setPerm(perm);
    projectUser.setCreateTime(now);
    projectUser.setModifyTime(now);

    projectUserMapper.insert(projectUser);

    return projectUser;
  }

  /**
   * 修改项目中用户的权限
   *
   * @param operator
   * @param name
   * @param userName
   * @param perm
   */
  public ProjectUser modifyProjectUser(User operator, String name, String userName, int perm) {
    Project project = projectMapper.queryByName(name);

    // 不存在的项目名
    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 操作用户不是项目的 owner
    if (operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner",operator.getName());
    }

    // 查询用户
    User user = userMapper.queryByName(userName);

    // 增加的用户不存在
    if (user == null) {
      throw new NotFoundException("user",userName);
    }

    ProjectUser projectUser = projectUserMapper.query(project.getId(), user.getId());

    // 修改的信息不存在
    if (projectUser == null) {
      throw new NotFoundException("project_user",name+" of "+userName);
    }

    // 构建信息, 插入
    projectUser = new ProjectUser();
    Date now = new Date();

    projectUser.setProjectId(project.getId());
    projectUser.setProjectName(project.getName());
    projectUser.setUserId(user.getId());
    projectUser.setUserName(userName);
    projectUser.setPerm(perm);
    projectUser.setModifyTime(now);

    projectUserMapper.modify(projectUser);

    return projectUser;
  }

  /**
   * 删除一个项目中的用户
   *
   * @param operator
   * @param name
   * @param userName
   */
  public void deleteProjectUser(User operator, String name, String userName) {
    // 查询项目
    Project project = projectMapper.queryByName(name);

    // 不存在的项目名
    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 操作用户不是项目的 owner
    if (operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner",operator.getName());
    }

    User user = userMapper.queryByName(userName);

    // 删除的用户不存在
    if (user == null) {
      throw new NotFoundException("user",userName);
    }

    // 不能删除自己
    if (operator.getId() == user.getId()) {
      throw new ParameterException("userName");
    }

    int count = projectUserMapper.delete(project.getId(), user.getId());

    if (count <= 0) {
      throw new NotModifiedException("Not delete count");
    }

    return;
  }

  /**
   * 查询一个项目下所有用户
   *
   * @param operator
   * @param name
   * @return
   */
  public List<ProjectUser> queryUser(User operator, String name) {
    // 查询项目
    Project project = projectMapper.queryByName(name);

    // 不存在的项目名
    if (project == null) {
      throw new NotFoundException("project",name);
    }

    // 操作用户不是项目的 owner
    if (operator.getId() != project.getOwnerId()) {
      throw new PermissionException("project owner",operator.getName());
    }

    return projectUserMapper.queryByProject(project.getId());
  }

  /**
   * 查询一个用户在项目中具备的权限
   *
   * @param userId
   * @param project
   * @return
   */
  public int queryPerm(int userId, Project project) {
    if (project.getOwnerId() == userId) {
      return Constants.PROJECT_USER_PERM_ALL;
    }

    ProjectUser projectUser = projectUserMapper.query(project.getId(), userId);

    if (projectUser == null) {
      return 0;
    }

    return projectUser.getPerm();
  }

  /**
   * 是否具备写权限
   *
   * @param userId
   * @param project
   * @return
   */
  public boolean hasWritePerm(int userId, Project project) {
    return PermUtil.hasWritePerm(queryPerm(userId, project));
  }

  /**
   * 是否具备读权限
   *
   * @param userId
   * @param project
   * @return
   */
  public boolean hasReadPerm(int userId, Project project) {
    return PermUtil.hasReadPerm(queryPerm(userId, project));
  }

  /**
   * 是否具备执行权限
   *
   * @param userId
   * @param project
   * @return
   */
  public boolean hasExecPerm(int userId, Project project) {
    return PermUtil.hasExecPerm(queryPerm(userId, project));
  }
}
