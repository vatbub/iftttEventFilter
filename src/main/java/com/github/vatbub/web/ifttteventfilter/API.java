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
import common.internet.Error;
import common.internet.Internet;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

/**
 * A quick and dirty draft of the api
 */
public class API extends HttpServlet {
    private static final String passRegexp = "[CD]+";
    // private static final String passRegexp = "(?=.*ligne)(?=.*[CD]+)";
    private static final String makerEventName = "pushalotevent";
    private static final String makerAPiKey = "dbjf67CBpZit4QOBthB0xW";

    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter writer;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        StringBuilder requestBody = new StringBuilder();
        String line;

        try {
            writer = response.getWriter();
        } catch (IOException e) {
            sendErrorMail("getWriter", "Unable not read request", e);
            e.printStackTrace();
            return;
        }

        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        } catch (IOException e) {
            Error error = new Error(e.getClass().getName() + " occurred while reading the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            sendErrorMail("ReadRequestBody", requestBody.toString(), e);
            return;
        }
        Passed res = new Passed();

        // parse the request
        if (!request.getContentType().equals("application/json")) {
            // bad content type
            Error error = new Error("content type must be application/json");
            writer.write(gson.toJson(error));
        }

        PushalotJSONRequest pushalotJSONRequest;

        try {
            System.out.println("Received request:");
            System.out.println(requestBody.toString());
            System.out.println("Request encoding is: " + request.getCharacterEncoding());
            pushalotJSONRequest = gson.fromJson(requestBody.toString(), PushalotJSONRequest.class);
        } catch (Exception e) {
            sendErrorMail("ParseJSON", requestBody.toString(), e);
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            return;
        }

        res.passed = pushalotJSONRequest.message.matches(passRegexp);
        System.out.println("Request passed: " + Boolean.toString(res.passed));

        // fire request to the maker channel
        try {
            if (res.passed) {
                Internet.sendEventToIFTTTMakerChannel(makerAPiKey, makerEventName, pushalotJSONRequest.message, pushalotJSONRequest.link, pushalotJSONRequest.imageURL);
            }
        } catch (IOException e) {
            // Will only happen if I did a typo above
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            sendErrorMail("ForwardToIFTTT", requestBody.toString(), e);
            return;
        }

        // Tell IFTTT the result of the operation
        writer.write(gson.toJson(res));
    }

    private void sendErrorMail(String phase, String requestBody, Throwable e) {
        final String username = "vatbubissues@gmail.com";
        final String password = "cfgtzhbnvfcdre456780uijhzgt67876ztghjkio897uztgfv";
        final String toAddress = "vatbub123+automatederrorreports@gmail.com";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(toAddress));
            message.setSubject("[iftttEventFilter] An error occurred in your application");
            message.setText("Exception occurred in phase: " + phase + "\n\nRequest that caused the exception:\n" + requestBody
                    + "\n\nStacktrace of the exception:\n" + ExceptionUtils.getFullStackTrace(e));

            Transport.send(message);

            System.out.println("Sent email with error message to " + toAddress);

        } catch (MessagingException e2) {
            throw new RuntimeException(e2);
        }
    }

    class Passed {
        boolean passed;
    }

    @SuppressWarnings({"CanBeFinal", "unused"})
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
}
