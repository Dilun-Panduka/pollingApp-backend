package com.example.dilun.polls.controller;

import com.example.dilun.polls.Repositories.PollRepository;
import com.example.dilun.polls.Repositories.UserRepository;
import com.example.dilun.polls.Repositories.VoteRepository;
import com.example.dilun.polls.models.Poll;
import com.example.dilun.polls.payload.*;
import com.example.dilun.polls.security.CurrentUser;
import com.example.dilun.polls.security.UserPrinciple;
import com.example.dilun.polls.services.PollService;
import com.example.dilun.polls.util.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollController.class);

    private final PollRepository pollRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final PollService pollService;

    @Autowired
    public PollController(PollRepository pollRepository, VoteRepository voteRepository, UserRepository userRepository, PollService pollService) {
        this.pollRepository = pollRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.pollService = pollService;
    }

    @GetMapping
    public PagedResponse<PollResponse> getPolls(@CurrentUser UserPrinciple currentUser,
                                                @RequestParam(value = "page", defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
                                                @RequestParam(value = "size", defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size) {
        return pollService.getAllPolls(currentUser, page, size);

    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createPoll(@Valid @RequestBody PollRequest pollRequest) {
        Poll poll = pollService.createPoll(pollRequest);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{pollId}").buildAndExpand(poll.getId()).toUri();

        return ResponseEntity.created(location).body(new ApiResponse(true, "Poll created Successfully"));
    }

    @GetMapping("/{pollId}")
    public PollResponse getPollById(@CurrentUser UserPrinciple currentUser, @PathVariable Long pollId) {
        return pollService.getPollById(pollId, currentUser);
    }

    @PostMapping("/{pollId}/votes")
    @PreAuthorize("hasRole('USER')")
    public PollResponse castVotes(@CurrentUser UserPrinciple currentUser, @PathVariable Long pollId, @Valid @RequestBody VoteRequest voteRequest) {
        return pollService.castVoteAndGetUpdatedPoll(pollId, voteRequest, currentUser);
    }

}
