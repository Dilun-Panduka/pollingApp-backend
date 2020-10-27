package com.example.dilun.polls.services;

import com.example.dilun.polls.Repositories.PollRepository;
import com.example.dilun.polls.Repositories.UserRepository;
import com.example.dilun.polls.Repositories.VoteRepository;
import com.example.dilun.polls.exception.BadRequestException;
import com.example.dilun.polls.exception.ResourceNotFoundException;
import com.example.dilun.polls.models.*;
import com.example.dilun.polls.payload.PagedResponse;
import com.example.dilun.polls.payload.PollRequest;
import com.example.dilun.polls.payload.PollResponse;
import com.example.dilun.polls.payload.VoteRequest;
import com.example.dilun.polls.security.UserPrinciple;
import com.example.dilun.polls.util.AppConstants;
import com.example.dilun.polls.util.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PollService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollService.class);

    private final PollRepository pollRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;

    @Autowired
    public PollService(PollRepository pollRepository, VoteRepository voteRepository, UserRepository userRepository) {
        this.pollRepository = pollRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
    }

    public PagedResponse<PollResponse> getAllPolls(UserPrinciple currentUser, int page, int size) {
        validatePageNumberAndSize(page, size);

        //Retrieve Polls
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAr");
        Page<Poll> polls = pollRepository.findAll(pageable);

        if (polls.getNumberOfElements() == 0) {
            return new PagedResponse<>(Collections.emptyList(),
                    polls.getNumber(), polls.getSize(), polls.getTotalElements(), polls.getTotalPages(), polls.isLast());
        }

        //Map polls to pollResponses containing vote counts and poll creators details
        List<Long> pollIds = polls.map(Poll::getId).getContent();
        Map<Long, Long> choiceVoteCountMap = getChoiceVoteCountMap(pollIds);
        Map<Long, Long> pollUserVoteMap = getPollUserVoteMap(currentUser, pollIds);
        Map<Long, User> creatorMap = getPollCreatorMap(polls.getContent());

        List<PollResponse> pollResponses = polls.map(poll -> ModelMapper.mapPollToPollResponse(poll,
                choiceVoteCountMap,
                creatorMap.get(poll.getCreatedBy()), pollUserVoteMap == null ? null : pollUserVoteMap.getOrDefault(poll.getId(), null))).getContent();

        return new PagedResponse<>(pollResponses,
                polls.getNumber(),
                polls.getSize(),
                polls.getTotalElements(),
                polls.getTotalPages(),
                polls.isLast()
        );
    }

    public PagedResponse<PollResponse> getPollsCreatedBy(String username, UserPrinciple currentUser, int page, int size) {
        validatePageNumberAndSize(page, size);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        Page<Poll> polls = pollRepository.findByCreatedBy(user.getId(), pageable);

        if (polls.getNumberOfElements() == 0) {
            return new PagedResponse<>(Collections.emptyList(),
                    polls.getNumber(), polls.getSize(),
                    polls.getTotalElements(), polls.getTotalPages(), polls.isLast());
        }
        //Map polls to pollResponses containing vote count and creators details
        List<Long> pollIds = polls.map(Poll::getId).getContent();
        Map<Long, Long> choiceVoteCountMap = getChoiceVoteCountMap(pollIds);
        Map<Long, Long> pollUservoteMap = getPollUserVoteMap(currentUser, pollIds);

        List<PollResponse> pollResponses = polls.map(poll -> ModelMapper.mapPollToPollResponse(poll,
                choiceVoteCountMap,
                user,
                pollUservoteMap == null ? null : pollUservoteMap.getOrDefault(poll.getId(), null)
        )).getContent();

        return new PagedResponse<>(pollResponses,
                polls.getNumber(), polls.getSize(),
                polls.getTotalElements(), polls.getTotalPages(), polls.isLast());
    }

    public PagedResponse<PollResponse> getPollsVotedBy(String username, UserPrinciple currentUser, int page, int size) {
        validatePageNumberAndSize(page, size);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        //Retrieve all pollIds in which the given user has voted
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        Page<Long> userVotedPollIds = voteRepository.findVotedPollIdsByUserId(user.getId(), pageable);

        if (userVotedPollIds.getNumberOfElements() == 0) {
            return new PagedResponse<>(Collections.emptyList(),
                    userVotedPollIds.getNumber(), userVotedPollIds.getSize(),
                    userVotedPollIds.getTotalElements(), userVotedPollIds.getTotalPages(),
                    userVotedPollIds.isLast());
        }

        //Retrieve all poll details from the voted pollIds
        List<Long> pollIds = userVotedPollIds.getContent();

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Poll> polls = pollRepository.findByIdIn(pollIds, sort);

        //Map poll to pollResponses containing vote counts and poll creators details
        Map<Long, Long> choiceVoteCountMap = getChoiceVoteCountMap(pollIds);
        Map<Long, Long> pollUserVoteMap = getPollUserVoteMap(currentUser, pollIds);
        Map<Long, User> creatorMap = getPollCreatorMap(polls);

        List<PollResponse> pollResponses = polls.stream().map(poll -> ModelMapper.mapPollToPollResponse(poll,
                choiceVoteCountMap,
                creatorMap.get(poll.getCreatedBy()),
                pollUserVoteMap == null ? null : pollUserVoteMap.getOrDefault(poll.getId(), null)))
                .collect(Collectors.toList());

        return new PagedResponse<>(pollResponses,
                userVotedPollIds.getNumber(), userVotedPollIds.getSize(),
                userVotedPollIds.getTotalElements(), userVotedPollIds.getTotalPages(),
                userVotedPollIds.isLast());
    }

    public Poll createPoll(PollRequest pollRequest) {
        Poll poll = new Poll();
        poll.setQuestion(pollRequest.getQuestion());

        pollRequest.getChoices().forEach(choiceRequest -> {
            poll.addChoice(new Choice(choiceRequest.getText()));
        });

        Instant now = Instant.now();
        Instant expirationDatetime = now
                .plus(Duration.ofDays(pollRequest.getPollLength().getDays()))
                .plus(Duration.ofHours(pollRequest.getPollLength().getHours()));
        poll.setExpirationDateTime(expirationDatetime);

        return pollRepository.save(poll);
    }

    public PollResponse getPollById(Long pollId, UserPrinciple currentUser) {

        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "id", pollId));

        //Retrieve Vote counts of every choice belong to current poll
        List<ChoiceVoteCount> votes = voteRepository.countByPollIdGroupByChoiceId(pollId);

        Map<Long, Long> choiceVoteMap = votes.stream()
                .collect(Collectors.toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));

        //Retrieve poll creator's details
        User creator = userRepository.findById(poll.getCreatedBy())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", poll.getCreatedBy()));

        //Retrieve vote done by logged in user
        Vote userVote = null;
        if (currentUser != null) {
            userVote = voteRepository.findByUserIdAndPollId(currentUser.getId(), pollId);
        }

        return ModelMapper.mapPollToPollResponse(poll,
                choiceVoteMap,
                creator,
                userVote != null ? userVote.getChoice().getId() : null);
    }

    public PollResponse castVoteAndGetUpdatedPoll(Long pollId, VoteRequest voteRequest, UserPrinciple currentUser) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "Id", pollId));

        if (poll.getExpirationDateTime().isBefore(Instant.now())) {
            throw new BadRequestException("Sorry! This poll has already expired");
        }

        User user = userRepository.getOne(currentUser.getId());

        Choice selectedChoice = poll.getChoices().stream()
                .filter(choice -> choice.getId().equals(voteRequest.getChoiceId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Choice", "id", voteRequest.getChoiceId()));

        Vote vote = new Vote();
        vote.setPoll(poll);
        vote.setUser(user);
        vote.setChoice(selectedChoice);

        try {
            vote = voteRepository.save(vote);
        } catch (DataIntegrityViolationException ex) {
            LOGGER.info("User {} has already voted in Poll {}", currentUser.getId(), pollId);
            throw new BadRequestException("Sorry! You have already cast your vote in this poll");
        }

        //----Vote is saved, Return Updated Poll Response----

        //Retrieve Vote Counts of every choice belongs to current poll
        List<ChoiceVoteCount> votes = voteRepository.countByPollIdGroupByChoiceId(pollId);

        Map<Long, Long> choiceVoteMap = votes.stream()
                .collect(Collectors.toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));

        //Retrieve Poll creator details
        User creator = userRepository.findById(poll.getCreatedBy())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", poll.getCreatedBy()));

        return ModelMapper.mapPollToPollResponse(poll, choiceVoteMap, creator, vote.getChoice().getId());

    }


    private void validatePageNumberAndSize(int page, int size) {

        if (page < 0) {
            throw new BadRequestException("Page number cannot be less than 0");
        }
        if (size > AppConstants.MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must not be greater than " + AppConstants.MAX_PAGE_SIZE);
        }

    }

    private Map<Long, Long> getChoiceVoteCountMap(List<Long> pollIds) {
        // Retrieve Vote Counts of every Choice belonging to the given pollIds
        List<ChoiceVoteCount> votes = voteRepository.countByPollIdInGroupByChoiceId(pollIds);

        Map<Long, Long> choiceVotesMap = votes.stream()
                .collect(Collectors
                        .toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));

        return choiceVotesMap;
    }

    private Map<Long, Long> getPollUserVoteMap(UserPrinciple currentUser, List<Long> pollIds) {
        //retrieve votes done by cuurent logged in user
        Map<Long, Long> pollUserVoteMap = null;
        if (currentUser != null) {
            List<Vote> userVotes = voteRepository.findByUserIdAndPollIdIn(currentUser.getId(), pollIds);
            pollUserVoteMap = userVotes.stream()
                    .collect(Collectors.
                            toMap(vote -> vote.getPoll().getId(), vote -> vote.getChoice().getId()));
        }
        return pollUserVoteMap;
    }

    Map<Long, User> getPollCreatorMap(List<Poll> polls) {
        //Get poll creators details from the given polls list
        List<Long> creatorIds = polls.stream()
                .map(Poll::getCreatedBy).distinct()
                .collect(Collectors.toList());

        List<User> creators = userRepository.findByIdIn(creatorIds);
        Map<Long, User> creatorMap = creators.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return creatorMap;
    }
}