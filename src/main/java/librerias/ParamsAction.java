package librerias;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class ParamsAction extends EventObject {

    private int action; // accion del caso de uso: add, modify, delete...
    private final List<String> params; // lista de parametros
    private final String sender; // correo que envio el comando
    private final String command; // comando recibido

    public ParamsAction(Object source, String sender, String command) {
        super(source);
        this.sender = sender;
        this.command = command;
        this.params = new ArrayList<>();
    }

    public List<String> getParams() {
        return params;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getSender() {
        return sender;
    }

    public String getCommand() {
        return command;
    }

    // Conserva los vacíos: cada parámetro se identifica por su posición.
    public void addParams(String param) {
        params.add(param);
    }
}
