package com.example.dilun.polls.security;

import com.example.dilun.polls.Repositories.UserRepository;
import com.example.dilun.polls.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with given username or email: "+usernameOrEmail));
        return UserPrinciple.create(user);
    }

    //used in JWTAuthenticationFilter
    @Transactional
    public UserDetails loadUserById(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for given ID: "+ id));
        return UserPrinciple.create(user);
    }
}
