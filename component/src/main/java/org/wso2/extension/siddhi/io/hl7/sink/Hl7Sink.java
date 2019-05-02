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
package org.wso2.extension.siddhi.io.hl7.sink;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.Initiator;
import ca.uhn.hl7v2.hoh.sockets.CustomCertificateTlsSocketFactory;
import ca.uhn.hl7v2.hoh.util.HapiSocketTlsFactoryWrapper;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.llp.MinLowerLayerProtocol;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.exception.ConnectionUnavailableException;
import io.siddhi.core.stream.ServiceDeploymentInfo;
import io.siddhi.core.stream.output.sink.Sink;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;
import io.siddhi.core.util.transport.DynamicOptions;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.query.api.definition.StreamDefinition;
import io.siddhi.query.api.exception.SiddhiAppValidationException;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.hl7.sink.exception.Hl7SinkRuntimeException;
import org.wso2.extension.siddhi.io.hl7.util.Hl7Constants;
import org.wso2.extension.siddhi.io.hl7.util.Hl7Utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * {@code Hl7Sink } Handle the Hl7 publishing tasks.
 */
@Extension(
        name = "hl7",
        namespace = "sink",
        description = "The hl7 sink publishes the hl7 messages using the MLLP protocol. ",
        parameters = {
                @Parameter(name = "uri",
                        description = "The URI that is used to connect to a HL7 Server. \n " +
                                "e.g.,\n" +
                                "'{hostname}:{port}' (recommended), or\n" +
                                "'hl7://{hostname}:{port}' \n",
                        type = {DataType.STRING}),

                @Parameter(name = "hl7.encoding",
                        description = "The encoding method of hl7. This can be 'er7' or 'xml'. You must define the" +
                                " hl7 encoding type based on the input. \n" +
                                "e.g., \n" +
                                "If the transmitting message is in the 'er7'(text) format, then the encoding type " +
                                "must be 'er7'. \n" +
                                "If the transmitting message is in the 'xml' format, then the encoding type must" +
                                " be 'xml'. ",
                        type = {DataType.STRING}),

                @Parameter(name = "hl7.ack.encoding",
                        description = "The encoding method of hl7 to log the acknowledgment message. This parameter" +
                                " can be specified as 'xml' if required. Otherwise, system uses the 'er7' format by" +
                                " default.",
                        optional = true, defaultValue = "ER7",
                        type = {DataType.STRING}),

                @Parameter(name = "charset",
                        description = "The character encoding method.'UTF-8' is used by default, but you can specify" +
                                " another method if required.",
                        optional = true, defaultValue = "UTF-8",
                        type = {DataType.STRING}),

                @Parameter(name = "tls.enabled",
                        description = "This parameter specifies whether an encrypted communication must be " +
                                "established or not. When this parameter is set to 'true', the 'tls.keystore.path'" +
                                " and 'tls.keystore.passphrase' parameters are initialized. ",
                        optional = true, defaultValue = "false",
                        type = {DataType.BOOL}),

                @Parameter(name = "tls.keystore.type",
                        description = "The type of the keystore. A custom keystore type can be specified " +
                                "if required. If no custom keystore type is specified, then the system uses " +
                                "'JKS' as the default keystore type. ",
                        optional = true, defaultValue = "JKS",
                        type = {DataType.STRING}),

                @Parameter(name = "tls.keystore.filepath",
                        description = "The file path to the keystore location of the client that sends " +
                                "the HL7 events via the 'MLLP' protocol. A custom keystore can be " +
                                "specified if required. If a custom keystore is not specified, then the system " +
                                "uses the default 'wso2carbon' keystore in the '${carbon.home}/resources/security' " +
                                "directory. ",
                        optional = true, defaultValue = "${carbon.home}/resources/security/wso2carbon.jks",
                        type = {DataType.STRING}),

                @Parameter(name = "tls.keystore.passphrase",
                        description = "The passphrase for the keystore. A custom passphrase can be specified " +
                                "if required. If no custom passphrase is specified, then the system uses " +
                                "'wso2carbon' as the default passphrase. ",
                        optional = true, defaultValue = "wso2carbon",
                        type = {DataType.STRING}),

                @Parameter(name = "hl7.timeout",
                        description = "The time interval (in milliseconds) for which the initiator must wait for a " +
                                "response for a given message before timing out and throwing an exception.",
                        optional = true, defaultValue = "10000",
                        type = {DataType.INT})
        },
        examples = {
                @Example(
                        syntax = "@App:name('Hl7TestAppForER7') \n" +
                                "@sink(type = 'hl7', \n" +
                                "uri = 'localhost:1080', \n" +
                                "hl7.encoding = 'er7', \n" +
                                "@map(type = 'text', @payload(\"{{{payload}}}\"))) \n" +
                                "define stream hl7stream(payload string); \n"
                        ,
                        description = "This publishes the HL7 messages in the 'ER7' format, and receives and logs " +
                                "the acknowledgement message in the console using the MLLP protocol and the custom " +
                                "text mapping."
                ),
                @Example(
                        syntax = "@App:name('Hl7TestAppForXML') \n" +
                                "@sink(type = 'hl7', \n" +
                                "uri = 'localhost:1080', \n" +
                                "hl7.encoding = 'xml', \n" +
                                "@map(type = 'xml', enclosing.element=\"<ADT_A01  xmlns='urn:hl7-org:v2xml'>\", " +
                                "@payload('<MSH><MSH.1>{{MSH1}}</MSH.1><MSH.2>{{MSH2}}</MSH.2><MSH.3><HD.1>" +
                                "{{MSH3HD1}}</HD.1></MSH.3><MSH.4><HD.1>{{MSH4HD1}}</HD.1></MSH.4><MSH.5><HD.1>" +
                                "{{MSH5HD1}}</HD.1></MSH.5><MSH.6><HD.1>{{MSH6HD1}}</HD.1></MSH.6><MSH.7>{{MSH7}}" +
                                "</MSH.7><MSH.8>{{MSH8}}</MSH.8><MSH.9><CM_MSG.1>{{CM_MSG1}}</CM_MSG.1><CM_MSG.2>" +
                                "{{CM_MSG2}}</CM_MSG.2></MSH.9><MSH.10>{{MSH10}}</MSH.10><MSH.11>" +
                                "{{MSH11}}</MSH.11><MSH.12>{{MSH12}}</MSH.12></MSH>'))) \n" +
                                "define stream hl7stream(MSH1 string, MSH2 string, MSH3HD1 string, MSH4HD1 string, " +
                                "MSH5HD1 string, MSH6HD1 string, MSH7 string, MSH8 string, CM_MSG1 string, " +
                                "CM_MSG2 string,MSH10 string,MSH11 string, MSH12 string); \n"
                        ,
                        description = "This publishes the HL7 messages in the XML format, and receives and logs the " +
                                "acknowledgement message in the console using the MLLP protocol and the custom " +
                                "XML mapping. \n "
                )
        }
)

public class Hl7Sink extends Sink {

    private static final Logger log = Logger.getLogger(Hl7Sink.class);
    private boolean tlsEnabled;
    private String charset;
    private String hl7Encoding;
    private String hl7AckEncoding;
    private int hl7Timeout;
    private Connection connection;
    private String tlsKeystoreFilepath;
    private String tlsKeystorePassphrase;
    private HapiContext hapiContext;
    private String hostName;
    private int port;
    private String tlsKeystoreType;
    private String streamID;
    private String siddhiAppName;
    private String uri;

    @Override
    public Class[] getSupportedInputEventClasses() {

        return new Class[]{String.class};
    }

    @Override
    protected ServiceDeploymentInfo exposeServiceDeploymentInfo() {
        return null;
    }

    @Override
    public String[] getSupportedDynamicOptions() {

        return new String[0];
    }

    @Override
    protected StateFactory init(StreamDefinition streamDefinition, OptionHolder optionHolder, ConfigReader configReader,
                                SiddhiAppContext siddhiAppContext) {

        this.siddhiAppName = siddhiAppContext.getName();
        this.streamID = streamDefinition.getId();
        this.uri = optionHolder.validateAndGetStaticValue(Hl7Constants.HL7_URI);
        this.hl7Encoding = optionHolder.validateAndGetStaticValue(Hl7Constants.HL7_ENCODING);
        this.charset = optionHolder.validateAndGetStaticValue(Hl7Constants.CHARSET_NAME,
                Hl7Constants.DEFAULT_HL7_CHARSET);
        this.hl7AckEncoding = optionHolder.validateAndGetStaticValue(Hl7Constants.ACK_HL7_ENCODING,
                Hl7Constants.DEFAULT_ACK_HL7_ENCODING);
        this.tlsEnabled = Boolean.parseBoolean(optionHolder.validateAndGetStaticValue(Hl7Constants.TLS_ENABLE,
                Hl7Constants.DEFAULT_TLS_ENABLE));
        this.hl7Timeout = Integer.parseInt(optionHolder.validateAndGetStaticValue(Hl7Constants.HL7_TIMEOUT,
                Hl7Constants.DEFAULT_HL7_TIMEOUT));
        this.tlsKeystoreFilepath = optionHolder.validateAndGetStaticValue(Hl7Constants.TLS_KEYSTORE_FILEPATH,
                Hl7Constants.DEFAULT_TLS_KEYSTORE_FILEPATH);
        this.tlsKeystorePassphrase = optionHolder.validateAndGetStaticValue(Hl7Constants.TLS_KEYSTORE_PASSPHRASE,
                Hl7Constants.DEFAULT_TLS_KEYSTORE_PASSPHRASE);
        this.tlsKeystoreType = optionHolder.validateAndGetStaticValue(Hl7Constants.TLS_KEYSTORE_TYPE,
                Hl7Constants.DEFAULT_TLS_KEYSTORE_TYPE);
        this.hapiContext = new DefaultHapiContext();
        getValuesFromUri();
        Hl7Utils.validateEncodingType(hl7Encoding, hl7AckEncoding, siddhiAppName, streamID);
        Hl7Utils.doTlsValidation(tlsEnabled, tlsKeystoreFilepath, tlsKeystorePassphrase, tlsKeystoreType,
                siddhiAppName, streamID);
        return null;
    }

    @Override
    public void publish(Object payload, DynamicOptions dynamicOptions, State state) {

        Initiator initiator = connection.getInitiator();
        String hl7Message = (String) payload;
        Parser pipeParser = hapiContext.getPipeParser();
        Parser xmlParser = hapiContext.getXMLParser();
        initiator.setTimeout(hl7Timeout, TimeUnit.MILLISECONDS);
        Message response;
        try {
            Message message;
            if (hl7Encoding.toUpperCase(Locale.ENGLISH).equals("ER7")) {
                message = pipeParser.parse(hl7Message);
            } else {
                message = xmlParser.parse(hl7Message);
            }
            response = initiator.sendAndReceive(message);
            try {
                String responseString;
                if (hl7AckEncoding.toUpperCase(Locale.ENGLISH).equals("ER7")) {
                    responseString = pipeParser.encode(response);
                } else {
                    responseString = xmlParser.encode(response);
                }
                log.info("Received Response from " + connection.getRemoteAddress() + ":" +
                        connection.getRemotePort() + " for stream " + siddhiAppName + ":" + streamID + ":\n" +
                        responseString.replaceAll("\r", "\n"));
            } catch (HL7Exception e) {
                throw new Hl7SinkRuntimeException("Error occurred while encoding the Received ACK Message " +
                        "into String for stream: " + siddhiAppName + ":" + streamID + ". ", e);
            }
        } catch (HL7Exception e) {
            log.error("Error occurred while processing the message. Please check the " + siddhiAppName + ":" +
                    streamID + ". " + e);
            throw new Hl7SinkRuntimeException("Error occurred while processing the message. Please check the " +
                    siddhiAppName + ":" + streamID + ". ", e);
        } catch (LLPException e) {
            throw new Hl7SinkRuntimeException("Error encountered with MLLP protocol for stream " + siddhiAppName +
                    ":" + streamID + ". ", e);
        } catch (IOException e) {
            log.error("Interruption occurred while sending the message from stream: " + siddhiAppName + ":" +
                    streamID + ". " + e);
            throw new Hl7SinkRuntimeException("Interruption occurred while sending the message from stream: " +
                    siddhiAppName + ":" + streamID + ". ", e);
        }
    }

    @Override
    public void connect() throws ConnectionUnavailableException {

        MinLowerLayerProtocol mllp = new MinLowerLayerProtocol();
        mllp.setCharset(charset);
        hapiContext.setLowerLayerProtocol(mllp);
        if (tlsEnabled) {
            CustomCertificateTlsSocketFactory tlsFac = new CustomCertificateTlsSocketFactory(tlsKeystoreType,
                    tlsKeystoreFilepath, tlsKeystorePassphrase);
            hapiContext.setSocketFactory(new HapiSocketTlsFactoryWrapper(tlsFac));
        }
        try {
            connection = hapiContext.newClient(hostName, port, tlsEnabled);
            log.info("Executing HL7Sender: HOST: " + hostName + ", PORT: " + port + " for stream " + siddhiAppName +
                    ":" + streamID + ". ");
        } catch (HL7Exception e) {
            throw new ConnectionUnavailableException("Failed to connect with the HL7 server, check " +
                    "the host.name = " + hostName + ", port = " + port + " defined in " + siddhiAppName + ":" +
                    streamID + ". ", e);
        }
    }

    @Override
    public void disconnect() {

        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public void destroy() {
        //Not applicable
    }

    private void getValuesFromUri() {

        String[] separator = uri.split(":");
        try {
            URI aURI = new URI(uri);
            if (separator.length == 2) {
                aURI = new URI("hl7://" + uri);
            } else if (separator.length == 3 && !aURI.getScheme().toUpperCase(Locale.ENGLISH).equals("HL7")) {
                throw new SiddhiAppValidationException("Invalid uri format defined in " + siddhiAppName + ":" +
                        streamID + ". Expected uri format is {host}:{port} or hl7://{host}:{port}. ");
            }
            hostName = aURI.getHost();
            port = aURI.getPort();
            if (hostName == null || port == -1) {
                throw new SiddhiAppValidationException("Invalid uri format defined in " + siddhiAppName + ":" +
                        streamID + ". Expected uri format is {host}:{port} or hl7://{host}:{port}. ");
            }
        } catch (URISyntaxException e) {
            throw new SiddhiAppValidationException("Invalid uri format defined in " + siddhiAppName + ":" +
                    streamID + ". Expected uri format is {host}:{port} or hl7://{host}:{port}. ", e);
        }
    }
}
