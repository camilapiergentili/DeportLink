package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import com.deportlink.deportlink.security.config.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT filter, method security, transactions and MySQL Flyway schema. */
@AutoConfigureMockMvc
class InstructorApiIntegrationTest extends OccupancyLifecycleFixture {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder encoder;
    String teacherToken, adminToken, playerToken, otherToken, ownerToken;
    Long teacherId;
    private static final String BASE="/api/instructor";
    private static final String PASSWORD="TestPassword123!";

    @BeforeEach void authenticateFixtures() throws Exception {
        teacherId=jdbc.queryForObject("SELECT instructor_id FROM class_slot WHERE id=?",Long.class,slotId);
        jdbc.update("UPDATE users SET role='INSTRUCTOR', password=? WHERE id=?",encoder.encode(PASSWORD),teacherId);
        jdbc.update("UPDATE users SET role='PLAYER' WHERE id=?",playerId);
        teacherToken=login(users.findById(teacherId).orElseThrow().getEmail());
        playerToken=jwt.generateToken(users.findById(playerId).orElseThrow());
        adminToken=jwt.generateToken(newUser(Rol.ADMIN));
        ownerToken=jwt.generateToken(newUser(Rol.OWNER));
        var other=register("teacher-"+UUID.randomUUID()+"@example.com");
        otherToken=login(other.path("email").asText());
    }
    private UserEntity newUser(Rol role) {
        var u=new UserEntity(); u.setEmail(UUID.randomUUID()+"@example.com");
        u.setFirstName("API"); u.setLastName("Test"); u.setRole(role);
        u.setPassword(encoder.encode(PASSWORD)); return users.saveAndFlush(u);
    }
    private String login(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
    private JsonNode register(String email) throws Exception {
        var response=mvc.perform(post("/api/admin/instructors").header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "firstName","Profe","lastName","Padel","email",email,"password",PASSWORD))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("INSTRUCTOR"))
            .andExpect(jsonPath("$.password").doesNotExist()).andReturn().getResponse();
        mvc.perform(get(response.getHeader("Location")).header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk());
        return json.readTree(response.getContentAsString());
    }
    private Map<String,Object> slotBody() {
        var body=new LinkedHashMap<String,Object>();
        body.put("courtId",courtId); body.put("dayOfWeek",day.getDayOfWeek().name());
        body.put("startTime","16:00:00"); body.put("durationMinutes",60);
        body.put("level","INTERMEDIO"); body.put("capacity",4);
        return body;
    }
    private JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var req=request(org.springframework.http.HttpMethod.valueOf(method),path);
        if(token!=null) req.header("Authorization","Bearer "+token);
        if(body!=null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result=mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank()?json.nullNode():json.readTree(result);
    }

    @Test void completeProfessorJourneyUsesRealLoginAndKeepsFourSessions() throws Exception {
        var me=call("GET","/api/me",teacherToken,null,200);
        assertEquals(teacherId.longValue(),me.path("id").asLong());
        assertEquals("INSTRUCTOR",me.path("role").asText());
        assertEquals(4,me.size());
        var body=slotBody();
        body.put("instructorId",999999); body.put("actorRole","ADMIN"); // ignored, never trusted
        var slot=call("POST",BASE+"/class-slots",teacherToken,body,201);
        long id=slot.path("id").asLong();
        assertEquals(teacherId.longValue(),slot.path("instructorId").asLong());
        assertEquals(60,slot.path("durationMinutes").asInt());
        assertEquals("16:00:00",slot.path("startTime").asText());
        var enrollment=call("POST",BASE+"/class-slots/"+id+"/players",teacherToken,Map.of("playerId",playerId),200);
        assertTrue(enrollment.path("active").asBoolean());
        // Explicit range on purpose: the default window is today..today+27, but the 4th generated
        // occurrence lands on today+28 once today's class time has already passed, so relying on
        // the default made this assertion depend on the time of day the test runs.
        var sessions=call("GET",BASE+"/class-sessions?from="+LocalDate.now()+"&to="+LocalDate.now().plusDays(35),teacherToken,null,200);
        assertEquals(4,sessions.size());
        assertEquals(4L,count("SELECT COUNT(*) FROM class_session WHERE class_slot_id=?",id));
        var summary=sessions.get(0);
        assertEquals(1,summary.path("occupancy").asInt());
        assertEquals(3,summary.path("availableSpots").asInt());
        assertFalse(summary.has("duration"));
        long sessionId=summary.path("sessionId").asLong();
        var detail=call("GET",BASE+"/class-sessions/"+sessionId,teacherToken,null,200);
        long attendanceId=detail.path("attendees").get(0).path("attendanceId").asLong();
        assertEquals("CONFIRMED",call("PUT",BASE+"/class-attendances/"+attendanceId+"/confirm",teacherToken,null,200).path("status").asText());
        call("PUT",BASE+"/class-attendances/"+attendanceId+"/confirm",teacherToken,null,200);
        call("PUT",BASE+"/class-attendances/"+attendanceId+"/cancel",teacherToken,null,200);
        call("PUT",BASE+"/class-attendances/"+attendanceId+"/cancel",teacherToken,null,200);
        call("DELETE",BASE+"/class-slots/"+id+"/players/"+playerId,teacherToken,null,200);
        call("PUT",BASE+"/class-attendances/"+attendanceId+"/confirm",teacherToken,null,404);
        call("POST",BASE+"/class-slots/"+id+"/players",teacherToken,Map.of("playerId",playerId),200);
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?",String.class,attendanceId));
        var group=call("GET",BASE+"/class-slots/"+id,teacherToken,null,200);
        assertEquals(1,group.path("players").size());
        assertFalse(group.toString().contains("email"));
        assertFalse(group.toString().contains("password"));
        call("PUT",BASE+"/class-slots/"+id+"/pause",teacherToken,null,200);
        call("PUT",BASE+"/class-slots/"+id+"/pause",teacherToken,null,409);
        call("PUT",BASE+"/class-slots/"+id+"/reactivate",teacherToken,null,200);
        assertEquals(4L,count("SELECT COUNT(*) FROM class_session WHERE class_slot_id=?",id));
    }

    @Test void anotherInstructorCannotReadOrMutateAnyPartOfTheGroup() throws Exception {
        var session=createSession.execute(admin,slotId,day);
        Long attendance=jdbc.queryForObject("SELECT id FROM class_attendance WHERE class_session_id=?",Long.class,session.id());
        assertEquals(0,call("GET",BASE+"/class-slots",otherToken,null,200).size());
        assertEquals(0,call("GET",BASE+"/class-sessions",otherToken,null,200).size());
        call("GET",BASE+"/class-slots/"+slotId,otherToken,null,404);
        call("PUT",BASE+"/class-slots/"+slotId+"/pause",otherToken,null,404);
        call("PUT",BASE+"/class-slots/"+slotId+"/reactivate",otherToken,null,404);
        call("POST",BASE+"/class-slots/"+slotId+"/players",otherToken,Map.of("playerId",playerId),404);
        call("DELETE",BASE+"/class-slots/"+slotId+"/players/"+playerId,otherToken,null,404);
        call("GET",BASE+"/class-sessions/"+session.id(),otherToken,null,404);
        call("PUT",BASE+"/class-attendances/"+attendance+"/confirm",otherToken,null,404);
        call("PUT",BASE+"/class-attendances/"+attendance+"/cancel",otherToken,null,404);
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT active_status FROM class_slot WHERE id=?",String.class,slotId));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?",String.class,attendance));
    }

    @Test void routesRequireAuthenticationAndRejectPlayerOwnerAndAdminOnProfessorPanel() throws Exception {
        String[] paths={"/class-slots","/class-slots/1","/class-sessions","/class-sessions/1","/courts"};
        for(String path:paths) {
            call("GET",BASE+path,null,null,401);
            for(String token:List.of(playerToken,ownerToken,adminToken))
                call("GET",BASE+path,token,null,403);
        }
        call("POST",BASE+"/class-slots",playerToken,slotBody(),403);
        call("POST",BASE+"/player-lookup",playerToken,Map.of("email","x@example.com"),403);
        call("PUT",BASE+"/class-attendances/1/confirm",playerToken,null,403);
        call("POST","/api/admin/instructors",teacherToken,Map.of("firstName","Test","lastName","Test",
            "email","x@example.com","password",PASSWORD),403);
        call("GET","/api/me",null,null,401);
        var invalid=call("GET","/api/me","invalid",null,401);
        assertEquals(401,invalid.path("status").asInt());
        assertEquals("/api/me",invalid.path("path").asText());
        assertTrue(invalid.has("method"));
    }

    @Test void registrationHashesPasswordAndRejectsEmailAcrossRolesWithoutPartialInstructor() throws Exception {
        String email=users.findById(playerId).orElseThrow().getEmail();
        long before=count("SELECT COUNT(*) FROM instructors");
        call("POST","/api/admin/instructors",adminToken,Map.of("firstName","Test","lastName","Test",
            "email",email,"password",PASSWORD),409);
        assertEquals(before,count("SELECT COUNT(*) FROM instructors"));
        var registered=register(UUID.randomUUID()+"@example.com");
        var stored=users.findById(registered.path("id").asLong()).orElseThrow();
        assertNotEquals(PASSWORD,stored.getPassword());
        assertTrue(encoder.matches(PASSWORD,stored.getPassword()));
        assertEquals(1L,count("SELECT COUNT(*) FROM instructors WHERE id=?",stored.getId()));
        assertFalse(registered.has("confirmPassword"));
    }

    @Test void authorizationUsesCurrentDatabaseRoleRatherThanStaleTokenClaims() throws Exception {
        jdbc.update("UPDATE users SET role='PLAYER' WHERE id=?",teacherId);
        call("GET",BASE+"/class-slots",teacherToken,null,403);
        assertEquals("PLAYER",call("GET","/api/me",teacherToken,null,200).path("role").asText());
    }

    @Test void selectorsReturnOnlyMinimalDataAndRespectEligibility() throws Exception {
        Long branch=jdbc.queryForObject("SELECT branch_id FROM court WHERE id=?",Long.class,courtId);
        var options=call("GET",BASE+"/courts?branchId="+branch+"&size=1",teacherToken,null,200);
        assertEquals(courtId.longValue(),options.path("items").get(0).path("courtId").asLong());
        assertFalse(options.path("hasNext").asBoolean());
        assertEquals(0,call("GET",BASE+"/courts?branchId="+branch+"&size=1&page=1",teacherToken,null,200).path("items").size());
        jdbc.update("UPDATE court SET active_status='INACTIVE' WHERE id=?",courtId);
        assertEquals(0,call("GET",BASE+"/courts?branchId="+branch,teacherToken,null,200).path("items").size());
        jdbc.update("UPDATE court SET active_status='ACTIVE' WHERE id=?",courtId);
        jdbc.update("UPDATE branches SET verification_status='PENDING' WHERE id=?",branch);
        assertEquals(0,call("GET",BASE+"/courts?branchId="+branch,teacherToken,null,200).path("items").size());
        String email=users.findById(playerId).orElseThrow().getEmail();
        var player=call("POST",BASE+"/player-lookup",teacherToken,Map.of("email",email),200);
        assertEquals(2,player.size());
        assertEquals(playerId.longValue(),player.path("playerId").asLong());
        call("POST",BASE+"/player-lookup",teacherToken,Map.of("email","not-present@example.com"),404);
        call("POST",BASE+"/player-lookup",teacherToken,Map.of("email",users.findById(teacherId).orElseThrow().getEmail()),404);
    }

    @Test void invalidBodiesAndParametersAre400WithoutSaving() throws Exception {
        for(var invalid:List.of(Map.of("capacity",5),Map.of("capacity",0),Map.of("durationMinutes",60.5),
                Map.of("durationMinutes",0),Map.of("dayOfWeek","JUEVES"),Map.of("level","EXPERTO"),
                Map.of("startTime","23:30:00"),Map.of("startTime","16:00:01"),Map.of("courtId",-1))) {
            var body=slotBody(); body.putAll(invalid);
            call("POST",BASE+"/class-slots",teacherToken,body,400);
        }
        mvc.perform(post(BASE+"/class-slots").header("Authorization","Bearer "+teacherToken)
            .contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest());
        call("POST",BASE+"/class-slots",teacherToken,Map.of(),400);
        for(String path:List.of("/class-slots/-1","/class-slots/not-a-number","/courts?size=51",
                "/courts?page=-1","/class-sessions?from=bad","/class-sessions?from=2026-09-10&to=2026-09-09",
                "/class-sessions?from=2026-01-01&to=2026-12-31"))
            call("GET",BASE+path,teacherToken,null,400);
        assertEquals(1L,count("SELECT COUNT(*) FROM class_slot WHERE court_id=?",courtId));
    }

    @Test void conflictsAre409AndFarFutureReadsDoNotGenerateSessions() throws Exception {
        var body=slotBody(); body.put("startTime","15:00:00");
        call("POST",BASE+"/class-slots",teacherToken,body,409);
        call("POST",BASE+"/class-slots/"+slotId+"/players",teacherToken,Map.of("playerId",playerId),409);
        LocalDate far=LocalDate.now().plusYears(1);
        assertEquals(0,call("GET",BASE+"/class-sessions?from="+far,teacherToken,null,200).size());
        assertEquals(0L,count("SELECT COUNT(*) FROM class_session WHERE class_slot_id=?",slotId));
    }

    @Test void openApiDescribesImplementedRoutesWithoutActorInput() throws Exception {
        String spec=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var tree=json.readTree(spec);
        assertTrue(tree.path("paths").has(BASE+"/class-slots"));
        assertTrue(tree.path("paths").has("/api/admin/instructors"));
        var parameters=tree.path("paths").path(BASE+"/class-slots").path("get").path("parameters");
        assertFalse(parameters.toString().contains("actor"));
        Files.writeString(Path.of("target/stage1d-openapi.json"),spec);
    }
}
