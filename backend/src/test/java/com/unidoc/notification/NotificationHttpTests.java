package com.unidoc.notification;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-notification-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/notification-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class NotificationHttpTests {
    @Autowired MockMvc mvc;
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;

    @Test
    void allRolesCanListOwnCountAndMarkRead() throws Exception {
        for (UserRole role : UserRole.values()) {
            User owner = user(role);
            String jwt = token(owner);
            var created = create(owner, NotificationType.REQUEST_SUBMITTED);
            mvc.perform(get("/api/notifications").header("Authorization", jwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(created.id()))
                    .andExpect(jsonPath("$.items[0].read").value(false))
                    .andExpect(jsonPath("$.items[0].readAt").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.items[0].recipient").doesNotExist());
            mvc.perform(get("/api/notifications/unread-count").header("Authorization", jwt))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
            mvc.perform(post("/api/notifications/{id}/read", created.id()).header("Authorization", jwt))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true))
                    .andExpect(jsonPath("$.readAt").exists());
            mvc.perform(post("/api/notifications/{id}/read", created.id()).header("Authorization", jwt))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
            mvc.perform(get("/api/notifications/unread-count").header("Authorization", jwt))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        }
    }

    @Test
    void filteredPagingAndMarkAllWorkWithoutModifyingOtherUsers() throws Exception {
        User alice = user(UserRole.STUDENT);
        User bob = user(UserRole.ADMIN);
        var one = create(alice, NotificationType.REQUEST_SUBMITTED);
        var two = create(alice, NotificationType.REQUEST_APPROVED);
        create(bob, NotificationType.REQUEST_REJECTED);
        String jwt = token(alice);
        mvc.perform(get("/api/notifications?type=REQUEST_APPROVED&read=false&page=0&size=1").header("Authorization", jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(two.id()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(post("/api/notifications/{id}/read", one.id()).header("Authorization", jwt)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications?read=true").header("Authorization", jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(one.id()))
                .andExpect(jsonPath("$.items[1]").doesNotExist());
        mvc.perform(post("/api/notifications/read-all").header("Authorization", jwt)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", token(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void wrongOwnerReturnsIdenticalSafeNotFoundAsUnknownId() throws Exception {
        User studentA = user(UserRole.STUDENT);
        User studentB = user(UserRole.STUDENT);
        long otherId = create(studentA, NotificationType.REQUEST_SUBMITTED).id();
        for (long id : new long[]{otherId, 9999999L}) {
            mvc.perform(post("/api/notifications/{id}/read", id).header("Authorization", token(studentB)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.requestId").exists())
                    .andExpect(jsonPath("$.recipientUserId").doesNotExist());
        }
    }

    @Test
    void invalidFiltersAndPaginationGiveStandardErrors() throws Exception {
        String jwt = token(user(UserRole.ADMIN));
        for (String query : new String[]{"type=UNKNOWN", "page=-1", "size=0", "size=101", "read=maybe"}) {
            mvc.perform(get("/api/notifications?" + query).header("Authorization", jwt))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.requestId").exists());
        }
    }

    @Test
    void anonymousCannotReadOrMutateAndDeleteIsUnavailable() throws Exception {
        for (String path : new String[]{"/api/notifications", "/api/notifications/unread-count"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-ID"));
        }
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/notifications")
                        .header("Authorization", token(user(UserRole.ADMIN))))
                .andExpect(status().isMethodNotAllowed());
    }

    private NotificationResponse create(User owner, NotificationType type) {
        return notifications.create(new NotificationCommand(owner.getId(), type,
                "Your document request", "A request update is available.", "/requests/123"));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("http-notification-" + UUID.randomUUID() + "@example.test", "hash", "Notification Test", role));
    }

    private String token(User user) {
        return "Bearer " + tokens.issue(user).value();
    }
}
