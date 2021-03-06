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
package com.baifendian.swordfish.common.storm;

import com.baifendian.swordfish.common.config.BaseConfig;
import com.baifendian.swordfish.common.job.struct.node.storm.dto.TopologyDto;
import com.baifendian.swordfish.common.job.struct.node.storm.dto.TopologyInfoDto;
import com.baifendian.swordfish.common.job.struct.node.storm.dto.TopologyOperationDto;
import com.baifendian.swordfish.common.job.struct.node.storm.dto.TopologySummaryDto;
import com.baifendian.swordfish.common.job.struct.node.storm.dto.TopologyWorkerDto;
import com.baifendian.swordfish.dao.enums.FlowStatus;
import com.baifendian.swordfish.dao.utils.json.JsonUtil;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storm rest 服务 API
 */
public class StormRestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseConfig.class);

  private static PropertiesConfiguration stormConf;

  private static String stormRestUrl;

  static {
    try {
      stormConf = new PropertiesConfiguration("common/storm.properties");
      stormRestUrl = stormConf.getString("storm.rest.url");
    } catch (ConfigurationException e) {
      LOGGER.error(e.getMessage(), e);
      System.exit(1);
    }
  }

  private final static String ACTIVE = "ACTIVE";

  private final static String INACTIVE = "INACTIVE";

  private final static String KILLED = "KILLED";

  private final static String REBALANCING = "REBALANCING";

  private final static String topologySummary = "/api/v1/topology/summary";

  private final static String topologyInfo = "/api/v1/topology/{0}";

  private final static String topologyKill = "/api/v1/topology/{0}/kill/{1}";

  private final static String topologyDeactivate = "/api/v1/topology/{0}/deactivate";

  private final static String topologyActivate = "/api/v1/topology/{0}/activate";

  /**
   * 获取 topologySummary url
   */
  private static String getTopologySummaryUrl() {
    return stormRestUrl + topologySummary;
  }

  /**
   * 获取 查询任务详细信息的 url
   */
  private static String getTopologyInfoUrl(String topologyId) {
    String url = stormRestUrl + MessageFormat.format(topologyInfo, topologyId);
    LOGGER.info("Get topology info url: {}", url);
    return url;
  }

  /**
   * 获取杀死一个任务的url
   */
  private static String getTopologyKillUrl(String topologyId, long waitTime) {
    String url =
        stormRestUrl + MessageFormat.format(topologyKill, topologyId, String.valueOf(waitTime));
    LOGGER.info("Get Topology kill url: {}", url);
    return url;
  }

  /**
   * 获取暂停一个任务rul
   */
  private static String getTopologyDeactivateUrl(String topologyId) {
    String url = stormRestUrl + MessageFormat.format(topologyDeactivate, topologyId);
    LOGGER.info("Get topology deactivate url: {}", url);
    return url;
  }

  /**
   * 恢复一个任务
   */
  private static String getTopologyActivateUrl(String topologyId) {
    String url = stormRestUrl + MessageFormat.format(topologyActivate, topologyId);
    LOGGER.info("Get topology activate url: {}", url);
    return url;
  }

  /**
   * 获取所有当前正在运行的任务的信息
   */
  public static TopologySummaryDto getTopologySummary() throws IOException {
    String res = Request.Get(getTopologySummaryUrl())
        .execute().returnContent().toString();

    return JsonUtil.parseObject(res, TopologySummaryDto.class);
  }


  /**
   * 通过名称获取ID,只能的当前正在运行的任务的ID
   */
  public static String getTopologyId(String topologyName) throws IOException {
    Optional<TopologyDto> result = getTopologySummary().getTopologies().stream()
        .filter(t -> StringUtils.equals(t.getName(), topologyName)).findFirst();

    if (result.isPresent()) {
      return result.get().getId();
    }

    return StringUtils.EMPTY;
  }

  /**
   * 通过Id获取任务详细信息
   */
  public static TopologyInfoDto getTopologyInfo(String topologyId) throws IOException {
    String res = Request.Get(getTopologyInfoUrl(topologyId))
        .execute().returnContent().toString();

    return JsonUtil.parseObject(res, TopologyInfoDto.class);
  }


  /**
   * 根据任务ID获取一个任务的状态
   */
  public static FlowStatus getTopologyStatus(String topologyId) throws IOException {
    TopologyInfoDto topologyInfo = getTopologyInfo(topologyId);
    if (topologyInfo == null || topologyInfo.getStatus() == null) {
      return null;
    }

    switch (topologyInfo.getStatus()) {
      case ACTIVE:
        return FlowStatus.RUNNING;
      case INACTIVE:
        return FlowStatus.INACTIVE;
      case KILLED:
        return FlowStatus.KILL;
      case REBALANCING:
        return FlowStatus.INIT;
      default:
        return FlowStatus.FAILED;
    }
  }

  /**
   * 获取一个任务所有的log
   */
  public static List<String> getTopologyLogs(String topologyId) throws Exception {
    List<String> logs = new ArrayList<>();
    TopologyInfoDto topologyInfo = getTopologyInfo(topologyId);
    for (TopologyWorkerDto work : topologyInfo.getWorkers()) {
      logs.add(work.getWorkerLogLink());
    }
    return logs;
  }


  /**
   * kill 一个任务
   */
  public static void topologyKill(String topologyId, long waitTime) throws Exception {
    String res = Request.Post(getTopologyKillUrl(topologyId, waitTime)).execute().returnContent()
        .toString();

    TopologyOperationDto topologyOperation = JsonUtil.parseObject(res, TopologyOperationDto.class);

    if (topologyOperation == null) {
      throw new Exception("kill not result return!");
    }

    if (!StringUtils.equalsIgnoreCase(topologyOperation.getStatus(), "success")) {
      String msg = MessageFormat
          .format("Kill status not equal success: {0}", topologyOperation.getStatus());
      throw new Exception(msg);
    }
  }

  /**
   * 暂停一个任务
   */
  public static void topologyDeactivate(String topologyId) throws Exception {
    String res = Request.Post(getTopologyDeactivateUrl(topologyId)).execute().returnContent()
        .toString();

    TopologyOperationDto topologyOperation = JsonUtil.parseObject(res, TopologyOperationDto.class);

    if (topologyOperation == null) {
      throw new Exception("Deactivate not result return!");
    }

    if (!StringUtils.equalsIgnoreCase(topologyOperation.getStatus(), "success")) {
      String msg = MessageFormat
          .format("Deactivate status not equal success: {0}", topologyOperation.getStatus());
      throw new Exception(msg);
    }
  }


  /**
   * 恢复一个任务
   */
  public static void topologyActivate(String topologyId) throws Exception {
    String res = Request.Post(getTopologyActivateUrl(topologyId)).execute().returnContent()
        .toString();

    TopologyOperationDto topologyOperation = JsonUtil.parseObject(res, TopologyOperationDto.class);

    if (topologyOperation == null) {
      throw new Exception("Activate not result return!");
    }

    if (!StringUtils.equalsIgnoreCase(topologyOperation.getStatus(), "success")) {
      String msg = MessageFormat
          .format("Activate status not equal success: {0}", topologyOperation.getStatus());
      throw new Exception(msg);
    }
  }
}
