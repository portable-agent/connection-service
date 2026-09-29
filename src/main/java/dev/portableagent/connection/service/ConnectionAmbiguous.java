package dev.portableagent.connection.service;

public class ConnectionAmbiguous extends RuntimeException {
    public ConnectionAmbiguous() {
        super("Several active connections require an explicit choice");
    }
}
