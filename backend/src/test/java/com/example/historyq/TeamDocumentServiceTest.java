package com.example.historyq;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamDocument;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import com.example.historyq.entity.User;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.repository.TeamDocumentRepository;
import com.example.historyq.repository.TeamMemberRepository;
import com.example.historyq.repository.TeamRepository;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.service.TeamDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamDocumentServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamDocumentRepository teamDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamDocumentService service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberCanSharePublicDocument() {
        User user = authenticatedUser();
        Team team = team();
        Document document = document("PUBLIC", user);

        givenMember(user, team);
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));
        when(teamDocumentRepository.existsByTeamIdAndDocumentId(
                team.getId(), document.getId())).thenReturn(false);
        when(teamDocumentRepository.save(any(TeamDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addDocuments(team.getId(), java.util.List.of(document.getId()));

        assertEquals(document.getId(), result.get(0).id());
        verify(teamDocumentRepository).save(any(TeamDocument.class));
    }

    @Test
    void memberCannotShareAnotherUsersPrivateDocument() {
        User user = authenticatedUser();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        Team team = team();
        Document document = document("PRIVATE", owner);

        givenMember(user, team);
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.addDocuments(
                        team.getId(), java.util.List.of(document.getId()))
        );
        verify(teamDocumentRepository, never()).save(any());
    }

    @Test
    void duplicateAssociationIsRejected() {
        User user = authenticatedUser();
        Team team = team();
        Document document = document("PUBLIC", user);

        givenMember(user, team);
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));
        when(teamDocumentRepository.existsByTeamIdAndDocumentId(
                team.getId(), document.getId())).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addDocuments(
                        team.getId(), java.util.List.of(document.getId()))
        );
        verify(teamDocumentRepository, never()).save(any());
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("member");
        user.setEmail("member@example.com");
        when(userRepository.findByUsername("member"))
                .thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "member", null, java.util.List.of()));
        return user;
    }

    private Team team() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Team");
        when(teamRepository.existsById(team.getId())).thenReturn(true);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        return team;
    }

    private void givenMember(User user, Team team) {
        TeamMember member = new TeamMember();
        member.setUser(user);
        member.setTeam(team);
        member.setRole(TeamRole.MEMBER);
        when(teamMemberRepository.findByTeamIdAndUserId(
                team.getId(), user.getId())).thenReturn(Optional.of(member));
    }

    private Document document(String visibility, User owner) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setDisplayName("Documento");
        document.setVisibility(visibility);
        document.setStatus("READY");
        document.setUploadedBy(owner);
        return document;
    }
}
