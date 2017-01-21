package com.github.vatbub.web.ifttteventfilter;

/*-
 * #%L
 * iftttEventFilter
 * %%
 * Copyright (C) 2016 - 2017 Frederik Kammel
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.Internet;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A quick and dirty draft of the api
 */
public class API extends HttpServlet {
    private static final String passRegexp = "[CD]+";
    private static final String makerEventName = "pushalotevent";
    private static final String makerAPiKey = "dbjf67CBpZit4QOBthB0xW";

    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter writer;

        try {
            writer = response.getWriter();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Passed res = new Passed();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // parse the request
        if (!request.getContentType().equals("application/json")) {
            // bad content type
            Error error = new Error("content type must be application/json");
            writer.write(gson.toJson(error));
        }

        StringBuilder requestBody = new StringBuilder();
        String line;
        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        } catch (IOException e) {
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            return;
        }

        System.out.println("Received request:");
        System.out.println(requestBody.toString());
        PushalotJSONRequest pushalotJSONRequest = gson.fromJson(requestBody.toString(), PushalotJSONRequest.class);

        res.passed = pushalotJSONRequest.message.matches(passRegexp);

        // fire request to the maker channel
        try {
            Internet.sendEventToIFTTTMakerChannel(makerAPiKey, makerEventName, pushalotJSONRequest.message, pushalotJSONRequest.link, pushalotJSONRequest.imageURL);
        } catch (IOException e) {
            // Will only happen if I did a typo above
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            return;
        }

        // Tell IFTTT the result of the operation
        writer.write(gson.toJson(res));
    }

    class Passed {
        boolean passed;
    }

    class PushalotJSONRequest {

        /*
        Sample JSON:
        {
            "message":"{{Text}}"
            "link":"{{LinkToTweet}}"
            "imageURL":"{{UserImageUrl}}"
        }
         */
        String message;
        String link;
        String imageURL;
    }

    class Error {
        String error;
        String stacktrace;

        Error(String error) {
            this(error, "");
        }

        Error(String error, String stacktrace) {
            this.error = error;
            this.stacktrace = stacktrace;
        }
    }
}
