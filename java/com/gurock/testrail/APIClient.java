package com.gurock.testrail;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

/**
 * TestRail API binding for Java (API v2, available since TestRail 3.0)
 * Updated for TestRail 5.7
 *
 * Learn more:
 *
 * http://docs.gurock.com/testrail-api2/start
 * http://docs.gurock.com/testrail-api2/accessing
 *
 * Copyright Gurock Software GmbH. See license.md for details.
 */
public class APIClient {
    private String m_user;
    private String m_password;
    private String m_url;
    private Gson gson = new Gson();

    public APIClient(String base_url) {
        if (!base_url.endsWith("/")) {
            base_url += "/";
        }
        this.m_url = base_url + "index.php?/api/v2/";
    }

    /**
     * Get the user used for authenticating the API requests.
     *
     * @return The user used for authenticating the API requests.
     */
    public String getUser() {
        return this.m_user;
    }

    /**
     * Set the user used for authenticating the API requests.
     *
     * @param user The user used for authenticating the API requests.
     */
    public void setUser(String user) {
        this.m_user = user;
    }

    /**
     * Get the password used for authenticating the API requests.
     *
     * @return The password used for authenticating the API requests.
     */
    public String getPassword() {
        return this.m_password;
    }

    /**
     * Set the password used for authenticating the API requests.
     *
     * @param password The password used for authenticating the API requests.
     */
    public void setPassword(String password) {
        this.m_password = password;
    }

    /**
     * Send a GET request to the API.
     *
     * @param uri  The API method to call including parameters.
     * @param data The data to be sent with the request.
     * @return The parsed JSON response.
     * @throws MalformedURLException If the URL is invalid.
     * @throws IOException           If an I/O error occurs.
     * @throws APIException          If an error occurs in the API.
     */
    public Object sendGet(String uri, String data) throws MalformedURLException, IOException, APIException {
        return this.sendRequest("GET", uri, data);
    }

    /**
     * Send a GET request to the API.
     *
     * @param uri The API method to call.
     * @return The parsed JSON response.
     * @throws MalformedURLException If the URL is invalid.
     * @throws IOException           If an I/O error occurs.
     * @throws APIException          If an error occurs in the API.
     */
    public Object sendGet(String uri) throws MalformedURLException, IOException, APIException {
        return this.sendRequest("GET", uri, null);
    }

    /**
     * Send a POST request to the API.
     *
     * @param uri  The API method to call including parameters.
     * @param data The data to be sent with the request.
     * @return The parsed JSON response.
     * @throws MalformedURLException If the URL is invalid.
     * @throws IOException           If an I/O error occurs.
     * @throws APIException          If an error occurs in the API.
     */
    public Object sendPost(String uri, Object data) throws MalformedURLException, IOException, APIException {
        return this.sendRequest("POST", uri, data);
    }

    private Object sendRequest(String method, String uri, Object data) throws MalformedURLException, IOException, APIException {
        URL url = new URL(this.m_url + uri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String auth = getAuthorization(this.m_user, this.m_password);
        conn.addRequestProperty("Authorization", "Basic " + auth);
        if (method.equals("POST")) {
            handlePostRequest(conn, uri, data);
        }
        int status = conn.getResponseCode();
        return processResponse(conn, uri, data);
    }

    private void handlePostRequest(HttpURLConnection conn, String uri, Object data) throws IOException {
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        if (data != null) {
            if (uri.startsWith("add_attachment")) {
                handleAttachmentRequest(conn, data);
            } else {
                handleJsonRequest(conn, data);
            }
        } else {
            conn.addRequestProperty("Content-Type", "application/json");
            OutputStream ostream = conn.getOutputStream();
            ostream.close();
        }
    }

    private Object processResponse(HttpURLConnection conn, String uri, Object data) throws IOException, APIException {
        int status = conn.getResponseCode();
        InputStream istream = (status != 200) ? conn.getErrorStream() : conn.getInputStream();
        if (status != 200) {
            throwExceptionForErrorResponse(status, istream);
        }
        if (istream != null && uri.startsWith("get_attachment/")) {
            saveAttachmentToFile(istream, (String) data);
            return (String) data;
        }
        return parseResponse(istream);
    }

    private Object parseResponse(InputStream istream) throws IOException {
        String text = "";
        if (istream != null) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            istream,
                            "UTF-8"
                    )
            );
            String line;
            while ((line = reader.readLine()) != null) {
                text += line;
                text += System.getProperty("line.separator");
            }
            reader.close();
        }
        JsonElement jsonElement = JsonParser.parseString(text);
        return gson.fromJson(jsonElement, Object.class);
    }

    private void handleAttachmentRequest(HttpURLConnection conn, Object data) throws IOException {
        String boundary = "TestRailAPIAttachmentBoundary";
        File uploadFile = new File((String) data);
        conn.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        OutputStream ostreamBody = conn.getOutputStream();
        BufferedWriter bodyWriter = new BufferedWriter(new OutputStreamWriter(ostreamBody));
        bodyWriter.write("\n\n--" + boundary + "\r\n");
        bodyWriter.write("Content-Disposition: form-data; name=\"attachment\"; filename=\"" + uploadFile.getName() + "\"");
        bodyWriter.write("\r\n\r\n");
        bodyWriter.flush();
        InputStream istreamFile = new FileInputStream(uploadFile);
        int bytesRead;
        byte[] dataBuffer = new byte[1024];
        while ((bytesRead = istreamFile.read(dataBuffer)) != -1) {
            ostreamBody.write(dataBuffer, 0, bytesRead);
        }
        ostreamBody.flush();
        bodyWriter.write("\r\n--" + boundary + "--\r\n");
        bodyWriter.flush();
        istreamFile.close();
        ostreamBody.close();
        bodyWriter.close();
    }

    private void throwExceptionForErrorResponse(int status, InputStream istream) throws APIException {
        throw new APIException(
                "TestRail API returned HTTP " + status +
                        " (No additional error message received)"
        );
    }

    private void saveAttachmentToFile(InputStream istream, String filePath) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(filePath);
        int bytesRead;
        byte[] buffer = new byte[1024];
        while ((bytesRead = istream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        istream.close();
    }

    private static String getAuthorization(String user, String password) {
        try {
            return new String(Base64.getEncoder().encode((user + ":" + password).getBytes()));
        } catch (IllegalArgumentException e) {
            // Not thrown
        }
        return "";
    }
}