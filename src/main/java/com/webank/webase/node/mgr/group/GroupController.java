/**
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.webank.webase.node.mgr.group;

import com.alibaba.fastjson.JSON;
import com.webank.webase.node.mgr.base.code.ConstantCode;
import com.webank.webase.node.mgr.base.controller.BaseController;
import com.webank.webase.node.mgr.base.entity.BasePageResponse;
import com.webank.webase.node.mgr.base.entity.BaseResponse;
import com.webank.webase.node.mgr.base.enums.DataStatus;
import com.webank.webase.node.mgr.base.exception.NodeMgrException;
import com.webank.webase.node.mgr.frontinterface.entity.GenerateGroupInfo;
import com.webank.webase.node.mgr.group.entity.GroupGeneral;
import com.webank.webase.node.mgr.group.entity.TbGroup;
import com.webank.webase.node.mgr.scheduler.ResetGroupListTask;
import com.webank.webase.node.mgr.scheduler.StatisticsTransdailyTask;
import com.webank.webase.node.mgr.transdaily.SeventDaysTrans;
import com.webank.webase.node.mgr.transdaily.TransDailyService;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for processing group information.
 */
@Log4j2
@RestController
@RequestMapping("group")
public class GroupController extends BaseController {

    @Autowired
    private GroupService groupService;
    @Autowired
    private TransDailyService transDailyService;
    @Autowired
    private StatisticsTransdailyTask statisticsTask;
    @Autowired
    private ResetGroupListTask resetGroupListTask;

    /**
     * generate group.
     */
    @PostMapping("/generate/{frontIp}/{frontPort}")
    public BaseResponse generateGroup(@RequestBody @Valid GenerateGroupInfo req,
            @PathVariable("frontIp") String frontIp, @PathVariable("frontPort") Integer frontPort,
            BindingResult result) throws NodeMgrException {
        checkBindResult(result);
        Instant startTime = Instant.now();
        BaseResponse baseResponse = new BaseResponse(ConstantCode.SUCCESS);
        log.info("start generateGroup startTime:{} groupId:{}", startTime.toEpochMilli(),
                req.getGenerateGroupId());
        groupService.generateGroup(req, frontIp, frontPort);
        log.info("end getGroupGeneral useTime:{} result:{}",
                Duration.between(startTime, Instant.now()).toMillis(),
                JSON.toJSONString(baseResponse));
        return baseResponse;
    }

    /**
     * start group.
     */
    @GetMapping("/start/{startGroupId}/{frontIp}/{frontPort}")
    public BaseResponse startGroup(@PathVariable("startGroupId") Integer startGroupId,
            @PathVariable("frontIp") String frontIp, @PathVariable("frontPort") Integer frontPort)
            throws NodeMgrException {
        Instant startTime = Instant.now();
        BaseResponse baseResponse = new BaseResponse(ConstantCode.SUCCESS);
        log.info("start startGroup startTime:{} groupId:{}", startTime.toEpochMilli(),
                startGroupId);
        groupService.startGroup(startGroupId, frontIp, frontPort);
        log.info("end startGroup useTime:{} result:{}",
                Duration.between(startTime, Instant.now()).toMillis(),
                JSON.toJSONString(baseResponse));
        return baseResponse;
    }

    /**
     * get group general.
     */
    @GetMapping("/general/{groupId}")
    public BaseResponse getGroupGeneral(@PathVariable("groupId") Integer groupId)
            throws NodeMgrException {
        Instant startTime = Instant.now();
        BaseResponse baseResponse = new BaseResponse(ConstantCode.SUCCESS);
        log.info("start getGroupGeneral startTime:{} groupId:{}", startTime.toEpochMilli(),
                groupId);
        GroupGeneral groupGeneral = null;

        int statisticTimes = 0;// if transCount less than blockNumber,statistics again
        while (true) {
            groupGeneral = groupService.queryGroupGeneral(groupId);
            BigInteger transactionCount = groupGeneral.getTransactionCount();
            BigInteger latestBlock = groupGeneral.getLatestBlock();
            if (transactionCount.compareTo(latestBlock) < 0 && statisticTimes == 0) {
                statisticTimes += 1;
                statisticsTask.updateTransdailyData();
                continue;
            } else {
                break;
            }
        }

        baseResponse.setData(groupGeneral);
        log.info("end getGroupGeneral useTime:{} result:{}",
                Duration.between(startTime, Instant.now()).toMillis(),
                JSON.toJSONString(baseResponse));
        return baseResponse;
    }

    /**
     * query all group.
     */
    @GetMapping("/all")
    public BasePageResponse getAllGroup() throws NodeMgrException {
        BasePageResponse pagesponse = new BasePageResponse(ConstantCode.SUCCESS);
        Instant startTime = Instant.now();
        log.info("start getAllGroup startTime:{}", startTime.toEpochMilli());

        // get group list
        int count = groupService.countOfGroup(null, DataStatus.NORMAL.getValue());
        if (count > 0) {
            List<TbGroup> groupList = groupService.getGroupList(DataStatus.NORMAL.getValue());
            pagesponse.setTotalCount(count);
            pagesponse.setData(groupList);
        }

        // reset group
        resetGroupListTask.asyncResetGroupList();

        log.info("end getAllGroup useTime:{} result:{}",
                Duration.between(startTime, Instant.now()).toMillis(),
                JSON.toJSONString(pagesponse));
        return pagesponse;
    }

    /**
     * get trans daily.
     */
    @GetMapping("/transDaily/{groupId}")
    public BaseResponse getTransDaily(@PathVariable("groupId") Integer groupId) throws Exception {
        BaseResponse pagesponse = new BaseResponse(ConstantCode.SUCCESS);
        Instant startTime = Instant.now();
        log.info("start getTransDaily startTime:{} groupId:{}", startTime.toEpochMilli(), groupId);

        // query trans daily
        List<SeventDaysTrans> listTrans = transDailyService.listSeventDayOfTrans(groupId);
        pagesponse.setData(listTrans);

        log.info("end getAllGroup useTime:{} result:{}",
                Duration.between(startTime, Instant.now()).toMillis(),
                JSON.toJSONString(pagesponse));
        return pagesponse;
    }
}
