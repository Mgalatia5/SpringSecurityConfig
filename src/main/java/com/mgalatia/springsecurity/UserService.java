package com.mgalatia.springsecurity;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username);
        if(user==null)
            throw new UsernameNotFoundException("User not found");
        return user;
    }
    public String create(User data) throws DataIntegrityViolationException
    {
        try {
            User user = User.builder()
                    .password(encoder.encode(data.getPassword()))
                    .email(data.getEmail())
                    .build();

            userRepository.save(user);

            return "User saved successful";
        }catch (DataIntegrityViolationException e){
            return "Email taken";
        } catch (Exception e) {
            return "Error "+e.getMessage();
        }
    }
}
