package com.petsitting.booking;

import java.util.Locale;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public enum OrderAction {
    PAY, CANCEL, REJECT, ACCEPT, START, COMPLETE, CONFIRM;
    public static OrderAction parse(String action) {
        try { return valueOf(action.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(BAD_REQUEST,"未知操作"); }
    }
    public boolean ownerAction() { return this==PAY||this==CANCEL||this==CONFIRM; }
}
