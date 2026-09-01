package com.vidyut.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.security.VidyutPrincipal;

@Component
public class CurrentUserUtil {

    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return null;
    }

    public Long getCurrentAccountId() {
        return principal().accountId();
    }

    public AccessMode getCurrentMode() {
        return principal().mode();
    }

    private VidyutPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof VidyutPrincipal principal)) {
            throw new IllegalStateException("No authenticated Vidyut account");
        }
        return principal;
    }
}
