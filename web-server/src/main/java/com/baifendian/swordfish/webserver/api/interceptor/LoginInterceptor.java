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

package com.baifendian.swordfish.webserver.api.interceptor;

import com.baifendian.swordfish.common.consts.Constants;
import com.baifendian.swordfish.common.utils.http.HttpUtil;
import com.baifendian.swordfish.dao.mysql.model.Session;
import com.baifendian.swordfish.dao.mysql.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

/**
 * Created by caojingwei on 16/8/22.
// */
//public class LoginInterceptor implements HandlerInterceptor {
//    @Autowired
//    private SessionService sessionService;
//
//    @Override
//    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) throws Exception {
//        Session session = sessionService.getSessionFromRequest(httpServletRequest);
//
//        //获取国际化语言
//        Cookie language = HttpUtil.getCookieByName(httpServletRequest, Constants.SESSION_LANGUAGE);
//        Locale locale = Constants.DEFAULT_LANGUAGE;
//        if (language != null) {
//            locale = chooseLocale(language.getValue());
//        }
//        LocaleContextHolder.setLocale(locale);
//        //httpServletRequest.setAttribute("language",locale);
//
//        // 传 idDebug 参数，则不需要校验 ,by dsfan
//        if (httpServletRequest.getParameter("isDebug") != null) {
//            Session session2 = new Session();
//            User user = new User();
//            user.setName("dsfan");
//            user.setId(2);
//            user.setTenantId(1);
//            user.setTenantName("bfd");
//            session2.setUser(user);
//            // httpServletRequest.setAttribute("session.userId", "2");
//            httpServletRequest.setAttribute("session", session2);
//            return true;
//        }
//
//        String path = httpServletRequest.getServletPath();
//        if (session != null) {
//            httpServletRequest.setAttribute("session.userId", session.getUserId());
//            httpServletRequest.setAttribute("session", session);
//            return true;
//        } else if (path.equals("/login") || path.equals("/isLogin")) {
//            return true;
//        } else if (path.equals("/user") && (httpServletRequest.getParameter("action").equals("create") || httpServletRequest.getParameter("action").equals("checkRegisterCode"))){
//            return true;
//        } else {
////            httpServletResponse.getWriter().write(JsonUtil.toJsonString(BaseResponse.SESSION_NOT_EXIST));
//            return false;
//        }
//    }
//
//    @Override
//    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
//        System.out.print(httpServletResponse);
//    }
//
//    @Override
//    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
//
//    }
//
//    /**
//     * 获取语言
//     * @param language
//     * @return
//     */
//    private Locale chooseLocale(String language){
//        switch (language){
//            case "zh" : return new Locale("zh","CN");
//            case "en" : return new Locale("en","US");
//            default : return Constants.DEFAULT_LANGUAGE;
//        }
//    }
//}
