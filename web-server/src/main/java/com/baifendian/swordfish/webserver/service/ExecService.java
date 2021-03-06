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

import com.baifendian.swordfish.dao.FlowDao;
import com.baifendian.swordfish.dao.enums.ExecType;
import com.baifendian.swordfish.dao.enums.FailurePolicyType;
import com.baifendian.swordfish.dao.enums.FlowStatus;
import com.baifendian.swordfish.dao.enums.NodeDepType;
import com.baifendian.swordfish.dao.enums.NotifyType;
import com.baifendian.swordfish.dao.mapper.ExecutionFlowMapper;
import com.baifendian.swordfish.dao.mapper.ExecutionNodeMapper;
import com.baifendian.swordfish.dao.mapper.MasterServerMapper;
import com.baifendian.swordfish.dao.mapper.ProjectMapper;
import com.baifendian.swordfish.dao.model.ExecutionFlow;
import com.baifendian.swordfish.dao.model.ExecutionNode;
import com.baifendian.swordfish.dao.model.MasterServer;
import com.baifendian.swordfish.dao.model.Project;
import com.baifendian.swordfish.dao.model.ProjectFlow;
import com.baifendian.swordfish.dao.model.User;
import com.baifendian.swordfish.dao.utils.json.JsonUtil;
import com.baifendian.swordfish.rpc.ExecInfo;
import com.baifendian.swordfish.rpc.RetResultInfo;
import com.baifendian.swordfish.rpc.ScheduleInfo;
import com.baifendian.swordfish.rpc.client.MasterClient;
import com.baifendian.swordfish.webserver.dto.ExecWorkflowsDto;
import com.baifendian.swordfish.webserver.dto.ExecutionFlowDto;
import com.baifendian.swordfish.webserver.dto.ExecutionNodeDto;
import com.baifendian.swordfish.webserver.dto.ExecutorIdDto;
import com.baifendian.swordfish.webserver.dto.ExecutorIdsDto;
import com.baifendian.swordfish.webserver.dto.LogResult;
import com.baifendian.swordfish.webserver.exception.NotFoundException;
import com.baifendian.swordfish.webserver.exception.ParameterException;
import com.baifendian.swordfish.webserver.exception.PermissionException;
import com.baifendian.swordfish.webserver.exception.PreFailedException;
import com.baifendian.swordfish.webserver.exception.ServerErrorException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExecService {

  private static Logger logger = LoggerFactory.getLogger(ExecService.class.getName());

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private MasterServerMapper masterServerMapper;

  @Autowired
  private ExecutionFlowMapper executionFlowMapper;

  @Autowired
  private ExecutionNodeMapper executionNodeMapper;

  @Autowired
  private WorkflowService workflowService;

  @Autowired
  private LogHelper logHelper;

  @Autowired
  private FlowDao flowDao;

  /**
   * 执行已经发布过的工作流
   */
  public ExecutorIdsDto postExecWorkflow(User operator, String projectName, String workflowName,
      String schedule, ExecType execType, FailurePolicyType failurePolicy, String nodeName,
      NodeDepType nodeDep, NotifyType notifyType, String notifyMails, int timeout) {

    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      logger.error("Project does not exist: {}", projectName);
      throw new NotFoundException("Not found project \"{0}\"", projectName);
    }

    // 必须有 project 写权限
    if (!projectService.hasWritePerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {}", operator.getName(),
          project.getName());
      throw new PermissionException("User \"{0}\" is not has project \"{1}\" write permission",
          operator.getName(), project.getName());
    }

    ProjectFlow projectFlow = flowDao.projectFlowfindByName(project.getId(), workflowName);

    if (projectFlow == null) {
      logger.error("Not found project flow {} in project {}", workflowName, project.getName());
      throw new NotFoundException("Not found project flow \"{0}\" in project \"{1}\"", workflowName,
          project.getName());
    }

    // 已经在运行的任务禁止重复提交
    ExecutionFlow executionFlow = executionFlowMapper
        .selectPreStartDate(projectFlow.getId(), new Date());
    if (executionFlow != null && executionFlow.getStatus() != null && !executionFlow.getStatus()
        .typeIsFinished()) {
      logger.error("The workflow is already running");
      throw new PreFailedException("The workflow \"{0}\" is already running",
          projectFlow.getName());
    }

    // 查看 master 是否存在
    MasterServer masterServer = masterServerMapper.query();
    if (masterServer == null) {
      logger.error("Master server does not exist.");
      throw new ServerErrorException("Master server does not exist.");
    }

    // 连接 executor server
    MasterClient masterClient = new MasterClient(masterServer.getHost(), masterServer.getPort());

    // 获取当前时间
    long now = new Date().getTime();

    try {
      logger
          .info("Call master client exec workflow, project id: {}, flow id: {}, host: {}, port: {}",
              project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());

      // 反序列化邮箱列表
      List<String> notifyMailList;

      try {
        notifyMailList = JsonUtil.parseObjectList(notifyMails, String.class);
      } catch (Exception e) {
        logger.error("notify mail list des11n error", e);
        throw new ParameterException("Notify mail \"{0}\" not valid", notifyMails);
      }

      switch (execType) {
        case DIRECT: {
          ExecInfo execInfo = new ExecInfo(nodeName, nodeDep != null ? nodeDep.ordinal() : 0,
              notifyType != null ? notifyType.ordinal() : 0, notifyMailList, timeout,
              failurePolicy != null? failurePolicy.ordinal(): 0);

          RetResultInfo retInfo = masterClient
              .execFlow(project.getId(), projectFlow.getId(), now, execInfo);

          if (retInfo == null || retInfo.getRetInfo().getStatus() != 0) {
            logger.error(
                "Call master client exec workflow false , project id: {}, flow id: {},host: {}, port: {}",
                project.getId(), projectFlow.getId(), masterServer.getHost(),
                masterServer.getPort());
            throw new ServerErrorException("master server return error");
          }

          return new ExecutorIdsDto(retInfo.getExecIds());
        }
        case COMPLEMENT_DATA: {
          // 反序列化调度信息
          ScheduleInfo scheduleInfo;

          try {
            scheduleInfo = JsonUtil.parseObject(schedule, ScheduleInfo.class);
          } catch (Exception e) {
            logger.error("scheduleInfo des11n error", e);
            throw new ParameterException("Schedule info \"{0}\" not valid", notifyMails);
          }

          ExecInfo execInfo = new ExecInfo(null, 0,
              notifyType != null ? notifyType.ordinal() : 0, notifyMailList, timeout,
              failurePolicy != null? failurePolicy.ordinal(): 0);

          RetResultInfo retInfo = masterClient
              .appendWorkFlow(project.getId(), projectFlow.getId(), scheduleInfo, execInfo);

          if (retInfo == null || retInfo.getRetInfo().getStatus() != 0) {
            logger.error(
                "Call master client append workflow data false , project id: {}, flow id: {},host: {}, port: {}",
                project.getId(), projectFlow.getId(), masterServer.getHost(),
                masterServer.getPort());

            throw new ServerErrorException(
                "Call master client append workflow data false , project id: {0}, flow id: {1},host: {2}, port: {3}",
                project.getId(), projectFlow.getId(), masterServer.getHost(),
                masterServer.getPort());
          }

          return new ExecutorIdsDto(retInfo.getExecIds());
        }
        default: {
          logger.error("exec workflow no support exec type {}", execType.name());
          throw new ParameterException("Exec type \"{0}\" not valid", execType.name());
        }
      }
    } catch (Exception e) {
      logger.error("Call master client exec workflow error", e);
      throw e;
    }
  }

  /**
   * 直接执行一个工作流
   */
  public ExecutorIdDto postExecWorkflowDirect(User operator, String projectName,
      String workflowName, String desc, String proxyUser, String queue, String data,
      MultipartFile file, FailurePolicyType failurePolicy, NotifyType notifyType,
      String notifyMails, int timeout, String extras) {
    logger.info("step1. create temp workflow");

    ProjectFlow projectFlow = workflowService
        .createWorkflow(operator, projectName, workflowName, desc, proxyUser, queue, data, file,
            extras, 1);

    if (projectFlow == null) {
      throw new ServerErrorException("project workflow create return is null");
    }

    logger.info("step2. exec temp workflow");

    ExecutorIdsDto executorIdsDto = postExecWorkflow(operator, projectName, workflowName, null,
        ExecType.DIRECT, failurePolicy, null, null, notifyType, notifyMails, timeout);
    if (CollectionUtils.isEmpty(executorIdsDto.getExecIds())) {
      throw new ServerErrorException("project workflow exec return is null");
    }

    return new ExecutorIdDto(executorIdsDto.getExecIds().get(0));
  }

  /**
   * 查询任务运行情况
   */
  public ExecWorkflowsDto getExecWorkflow(User operator, String projectName, String workflowName,
      Date startDate, Date endDate, String status, int from, int size) {

    List<String> workflowList = new ArrayList<>();

    if (StringUtils.isNotEmpty(workflowName)) {
      try {
        workflowList = JsonUtil.parseObjectList(workflowName, String.class);
      } catch (Exception e) {
        logger.error("des11n workflow list error", e);
        throw new ParameterException("Workflow name \"{0}\" not valid", workflowName);
      }
    }

    Project project = projectMapper.queryByName(projectName);
    if (project == null) {
      logger.error("Project does not exist: {}", projectName);
      throw new NotFoundException("Not found project \"{0}\"", projectName);
    }

    // project 必须有执行权限
    if (!projectService.hasExecPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {}", operator.getName(),
          project.getName());
      throw new PermissionException("User \"{0}\" is not has project \"{1}\" exec permission",
          operator.getName(), project.getName());
    }

    List<FlowStatus> flowStatusList;
    try {
      flowStatusList = JsonUtil.parseObjectList(status, FlowStatus.class);
    } catch (Exception e) {
      logger.error("flow status list des11n error", e);
      throw new ParameterException("Flow status list \"{0}\" not valid", status);
    }

    List<ExecutionFlow> executionFlowList = executionFlowMapper
        .selectByFlowIdAndTimesAndStatusLimit(projectName, workflowList, startDate, endDate, from,
            size, flowStatusList);
    List<ExecutionFlowDto> executionFlowResponseList = new ArrayList<>();
    for (ExecutionFlow executionFlow : executionFlowList) {
      executionFlowResponseList.add(new ExecutionFlowDto(executionFlow));
    }

    int total = executionFlowMapper
        .sumByFlowIdAndTimesAndStatus(projectName, workflowList, startDate, endDate,
            flowStatusList);
    return new ExecWorkflowsDto(total, from, executionFlowResponseList);
  }

  /**
   * 查询具体某个任务的运行情况
   */
  public ExecutionFlowDto getExecWorkflow(User operator, int execId) {

    ExecutionFlow executionFlow = executionFlowMapper.selectByExecId(execId);

    if (executionFlow == null) {
      logger.error("exec flow does not exist: {}", execId);
      throw new NotFoundException("Not found execId \"{0}\"", execId);
    }

    Project project = projectMapper.queryByName(executionFlow.getProjectName());
    if (project == null) {
      logger.error("Project does not exist: {}", executionFlow.getProjectName());
      throw new NotFoundException("Not found project \"{0}\"", executionFlow.getProjectName());
    }

    // 必须有 project 执行权限
    if (!projectService.hasExecPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {}", operator.getName(),
          project.getName());
      throw new PermissionException("User \"{0}\" is not has project \"{1}\" exec permission",
          operator.getName(), project.getName());
    }

    ExecutionFlowDto executionFlowDto = new ExecutionFlowDto(executionFlow);
    List<ExecutionNode> executionNodeList = executionNodeMapper.selectExecNodeById(execId);

    for (ExecutionNodeDto executionNodeResponse : executionFlowDto.getData().getNodes()) {
      for (ExecutionNode executionNode : executionNodeList) {
        if (StringUtils.equals(executionNodeResponse.getName(), executionNode.getName())) {
          executionNodeResponse.mergeExecutionNode(executionNode);
        }
      }
    }

    return executionFlowDto;
  }

  /**
   * 查询日志信息
   */
  public LogResult getEexcWorkflowLog(User operator, String jobId, int from, int size, String query) {
    ExecutionNode executionNode = executionNodeMapper.selectExecNodeByJobId(jobId);

    if (executionNode == null) {
      logger.error("job id does not exist: {}", jobId);
      throw new NotFoundException("Not found jobId \"{0}\"", jobId);
    }

    ExecutionFlow executionFlow = executionFlowMapper.selectByExecId(executionNode.getExecId());

    if (executionFlow == null) {
      logger.error("exec flow does not exist: {}", executionNode.getExecId());
      throw new NotFoundException("Not found execId \"{0}\"", executionNode.getExecId());
    }

    Project project = projectMapper.queryByName(executionFlow.getProjectName());

    if (project == null) {
      logger.error("Project does not exist: {}", executionFlow.getProjectName());
      throw new NotFoundException("Not found project \"{0}\"", executionFlow.getProjectName());
    }

    // 必须有 project 执行权限
    if (!projectService.hasExecPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {}", operator.getName(),
          project.getName());
      throw new PermissionException("User \"{0}\" is not has project \"{1}\" exec permission",
          operator.getName(), project.getName());
    }

    return logHelper.getLog(from, size, query, jobId);
  }

  /**
   * 停止运行
   */
  public void postKillWorkflow(User operator, int execId) {

    ExecutionFlow executionFlow = executionFlowMapper.selectByExecId(execId);

    if (executionFlow == null) {
      logger.error("exec flow does not exist: {}", execId);
      throw new NotFoundException("Not found execId \"{0}\"", execId);
    }

    Project project = projectMapper.queryByName(executionFlow.getProjectName());
    if (project == null) {
      logger.error("Project does not exist: {}", executionFlow.getProjectName());
      throw new NotFoundException("Not found project \"{0}\"", executionFlow.getProjectName());
    }

    // 必须有 project 执行权限
    if (!projectService.hasExecPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {}", operator.getName(),
          project.getName());
      throw new PermissionException("User \"{0}\" is not has project \"{1}\" exec permission",
          operator.getName(), project.getName());
    }

    MasterServer masterServer = masterServerMapper.query();
    if (masterServer == null) {
      logger.error("Master server does not exist.");
      throw new ServerErrorException("Master server does not exist.");
    }

    MasterClient masterClient = new MasterClient(masterServer.getHost(), masterServer.getPort());
    try {
      logger
          .info("Call master client kill workflow , project id: {}, flow id: {},host: {}, port: {}",
              project.getId(), executionFlow.getWorkflowName(), masterServer.getHost(),
              masterServer.getPort());
      if (!masterClient.cancelExecFlow(execId)) {
        logger.error(
            "Call master client kill workflow false , project id: {}, exec flow id: {}, host: {}, port: {}",
            project.getId(), execId, masterServer.getHost(), masterServer.getPort());
        throw new ServerErrorException(
            "Call master client kill workflow false , project id: \"{0}\", exec flow id: \"{1}\", host: \"{2}\", port: \"{3}\"",
            project.getId(), execId, masterServer.getHost(), masterServer.getPort());
      }
    } catch (Exception e) {
      logger.error("Call master client set schedule error", e);
      throw e;
    }
  }
}
