package de.unibayreuth.se.taskboard.data.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibayreuth.se.taskboard.business.domain.User;
import de.unibayreuth.se.taskboard.business.exceptions.DuplicateNameException;
import de.unibayreuth.se.taskboard.business.exceptions.UserNotFoundException;
import de.unibayreuth.se.taskboard.business.ports.UserPersistenceService;
import de.unibayreuth.se.taskboard.data.mapper.TaskEntityMapper;
import de.unibayreuth.se.taskboard.data.mapper.UserEntityMapper;
import de.unibayreuth.se.taskboard.data.persistence.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Primary
public class UserPersistenceServiceEventSourcingImpl implements UserPersistenceService {
    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;
    private final EventRepository eventRepository;
    private final TaskEntityMapper taskEntityMapper;
    private final ObjectMapper objectMapper;


    @Override
    public void clear() {
        userRepository.findAll()
                .forEach(userEntity -> eventRepository.saveAndFlush(
                        EventEntity.deleteEventOf(userEntityMapper.fromEntity(userEntity), null))
                );
        if (userRepository.count() != 0) {
            throw new IllegalStateException("Tasks not successfully deleted.");
        }
    }

    @NonNull
    @Override
    public List<User> getAll() {
        return userRepository.findAll().stream()
                .map(userEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Optional<User> getById(UUID id) {
        return userRepository.findById(id)
                .map(userEntityMapper::fromEntity);
    }

    @NonNull
    @Override
    public User upsert(User user) throws UserNotFoundException, DuplicateNameException {
        if (user.getId() == null) {
            UserEntity userEntity = userEntityMapper.toEntity(user);
            userEntity.setCreatedAt(LocalDateTime.now());

            UserEntity savedUserEntity = userRepository.save(userEntity);

            EventEntity insertEvent = EventEntity.insertEventOf(
                    userEntityMapper.fromEntity(savedUserEntity),
                    savedUserEntity.getId(),
                    objectMapper
            );
            eventRepository.saveAndFlush(insertEvent);

            return userEntityMapper.fromEntity(savedUserEntity);
        } else {
            // Update existing user
            UserEntity userEntity = userRepository.findById(user.getId())
                    .orElseThrow(() -> new EntityNotFoundException("User with ID " + user.getId() + " not found"));

            userEntity.setCreatedAt(user.getCreatedAt());
            userEntity.setName(user.getName());

            UserEntity updatedUserEntity = userRepository.saveAndFlush(userEntity);

            EventEntity updateUser = EventEntity.updateEventOf(
                    user,
                    user.getId(),
                    objectMapper
            );
            eventRepository.saveAndFlush(updateUser);

            return userEntityMapper.fromEntity(updatedUserEntity);
        }

        /*
        The upsert method in the UserPersistenceServiceEventSourcingImpl class handles both the creation and updating of users.
        If the user ID is null, it creates a new user by generating a new UUID, saving an insert event, and returning the newly created user.
        If the user ID is not null, it updates the existing user by finding it in the repository, updating its fields, saving an update event, and returning the updated user.
        In both cases, it uses the EventRepository to log the changes and the UserRepository to persist the user data.
        */

    }
}
