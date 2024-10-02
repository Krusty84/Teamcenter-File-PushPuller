//==================================================
// 
//  Copyright 2020 Siemens Digital Industries Software
//
//==================================================

package com.teamcenter.clientx;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Vector;


import com.teamcenter.schemas.soa._2006_03.exceptions.InvalidCredentialsException;
import com.teamcenter.schemas.soa._2006_03.exceptions.ServiceException;
import com.teamcenter.services.strong.core.DataManagementService;
import com.teamcenter.services.strong.core.SessionService;
import com.teamcenter.services.strong.core._2006_03.Session.LoginResponse;
import com.teamcenter.soa.SoaConstants;
import com.teamcenter.soa.client.Connection;
import com.teamcenter.soa.client.model.ModelObject;
import com.teamcenter.soa.client.model.strong.User;
import com.teamcenter.soa.client.model.strong.WorkspaceObject;
import com.teamcenter.soa.exceptions.CanceledOperationException;
import com.teamcenter.soa.exceptions.NotLoadedException;


public class AppXSession {
    /**
     * Single instance of the Connection object that is shared throughout
     * the application. This Connection object is needed whenever a Service
     * stub is instantiated.
     */
    private static Connection connection;

    /**
     * The credentialManager is used both by the Session class and the Teamcenter
     * Services Framework to get user credentials.
     */
    private static AppXCredentialManager credentialManager;

    /**
     * Create an instance of the Session with a connection to the specified
     * server.
     * <p>
     * Add implementations of the ExceptionHandler, PartialErrorListener,
     * ChangeListener, and DeleteListeners.
     *
     * @param host Address of the host to connect to, http://serverName:port/tc
     */
    public AppXSession(String host) {
        // Create an instance of the CredentialManager, this is used
        // by the SOA Framework to get the user's credentials when
        // challenged by the server (session timeout on the web tier).
        credentialManager = new AppXCredentialManager();

        String protocol = null;
        String envNameTccs = null;
        if (host.startsWith("http")) {
            protocol = SoaConstants.HTTP;
        } else if (host.startsWith("tccs")) {
            protocol = SoaConstants.TCCS;
            host = host.trim();
            int envNameStart = host.indexOf('/') + 2;
            envNameTccs = host.substring(envNameStart, host.length());
            host = "";
        } else {
            protocol = SoaConstants.IIOP;
        }


        // Create the Connection object, no contact is made with the server
        // until a service request is made
        connection = new Connection(host, credentialManager, SoaConstants.REST, protocol);

        if (protocol == SoaConstants.TCCS) {
            connection.setOption(Connection.TCCS_ENV_NAME, envNameTccs);
        }


        // Add an ExceptionHandler to the Connection, this will handle any
        // InternalServerException, communication errors, XML marshaling errors
        // .etc
        connection.setExceptionHandler(new AppXExceptionHandler());

        // While the above ExceptionHandler is required, all of the following
        // Listeners are optional. Client application can add as many or as few Listeners
        // of each type that they want.

        // Add a Partial Error Listener, this will be notified when ever a
        // a service returns partial errors.
        connection.getModelManager().addPartialErrorListener(new AppXPartialErrorListener());

        // Add a Change and Delete Listener, this will be notified when ever a
        // a service returns model objects that have been updated or deleted.
        connection.getModelManager().addModelEventListener(new AppXModelEventListener());


        // Add a Request Listener, this will be notified before and after each
        // service request is sent to the server.
        Connection.addRequestListener(new AppXRequestListener());

    }

    /**
     * Get the single Connection object for the application
     *
     * @return connection
     */
    public static Connection getConnection() {
        return connection;
    }

    /**
     * Login to the Teamcenter Server
     */
    public User login(String userName, String userPassword) {
        // Get the service stub
        SessionService sessionService = SessionService.getService(connection);
        LoginResponse out = null;
        try {
            out = sessionService.login(userName, userPassword,
                    "", "", "", "");
            return out.user;
        } catch (InvalidCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Terminate the session with the Teamcenter Server
     */
    public void logout() {
        // Get the service stub
        SessionService sessionService = SessionService.getService(connection);
        try {
            // *****************************
            // Execute the service operation
            // *****************************
            sessionService.logout();
        } catch (ServiceException e) {
        }
    }

    /**
     * Print some basic information for a list of objects
     *
     * @param objects
     */
    public static void printObjects(ModelObject[] objects) {
        if (objects == null)
            return;

        SimpleDateFormat format = new SimpleDateFormat("M/d/yyyy h:mm a", new Locale("en", "US")); // Simple no time zone

        // Ensure that the referenced User objects that we will use below are loaded
        getUsers(objects);

        System.out.println("Name\t\tOwner\t\tLast Modified");
        System.out.println("====\t\t=====\t\t=============");
        for (int i = 0; i < objects.length; i++) {
            if (!(objects[i] instanceof WorkspaceObject))
                continue;

            WorkspaceObject wo = (WorkspaceObject) objects[i];
            try {
                String name = wo.get_object_string();
                User owner = (User) wo.get_owning_user();
                Calendar lastModified = wo.get_last_mod_date();

                System.out.println(name + "\t" + owner.get_user_name() + "\t"
                        + format.format(lastModified.getTime()));
            } catch (NotLoadedException e) {
                // Print out a message, and skip to the next item in the folder
                // Could do a DataManagementService.getProperties call at this point
                System.out.println(e.getMessage());
                System.out.println("The Object Property Policy ($TC_DATA/soa/policies/Default.xml) is not configured with this property.");
            }
        }

    }


    private static void getUsers(ModelObject[] objects) {
        if (objects == null)
            return;

        DataManagementService dmService = DataManagementService.getService(AppXSession.getConnection());

        List<User> unKnownUsers = new Vector<User>();
        for (int i = 0; i < objects.length; i++) {
            if (!(objects[i] instanceof WorkspaceObject))
                continue;

            WorkspaceObject wo = (WorkspaceObject) objects[i];

            User owner = null;
            try {
                owner = (User) wo.get_owning_user();
                owner.get_user_name();
            } catch (NotLoadedException e) {
                if (owner != null)
                    unKnownUsers.add(owner);
            }
        }
        User[] users = (User[]) unKnownUsers.toArray(new User[unKnownUsers.size()]);
        String[] attributes = {"user_name"};


        // *****************************
        // Execute the service operation
        // *****************************
        dmService.getProperties(users, attributes);


    }


}
