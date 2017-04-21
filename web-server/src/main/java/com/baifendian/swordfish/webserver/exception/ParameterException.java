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
package com.baifendian.swordfish.webserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.text.MessageFormat;

/**
 * Created by caojingwei on 2017/4/20.
 */
public class ParameterException extends BadRequestException {

  private String parameter;

  public ParameterException(String parameter) {
    super("Parameter: {0} is invalid",parameter);
    this.parameter = parameter;
  }

  public String getParameter() {
    return parameter;
  }

  /**
   *
   */
  public ParameterException() {
    super();
  }

}
