/*
 *  Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package io.siddhi.extension.io.hl7.source;

import ca.uhn.hl7v2.protocol.ReceivingApplicationExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * HL7RExceptionHandler allows applications to handle parsing and handling errors.
 */
public class Hl7ExceptionHandler implements ReceivingApplicationExceptionHandler {

    private static final Logger log = LogManager.getLogger(Hl7ExceptionHandler.class);

    @Override
    public String processException(String s, Map<String, Object> map, String outGoingMsg, Exception e) {

        log.error("Some error occurred while process the message. Error message: " + e.getMessage());
        return outGoingMsg;
    }
}
