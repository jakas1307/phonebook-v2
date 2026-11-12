package com.pb.phonebook.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pb.phonebook.dto.ExtensionDto;
import com.pb.phonebook.dto.UserDto;
import com.pb.phonebook.entity.Company;
import com.pb.phonebook.entity.Extension;
import com.pb.phonebook.entity.Location;
import com.pb.phonebook.entity.User;
import com.pb.phonebook.repository.CompanyRepository;
import com.pb.phonebook.repository.LocationRepository;
import com.pb.phonebook.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;

    private UserDto mapToDto(User user) {
        List<ExtensionDto> extensionDtos = user.getExtensions().stream()
                .map(ext -> ExtensionDto.builder()
                        .id(ext.getId())
                        .type(ext.getType())
                        .number(ext.getNumber())
                        .build())
                .toList();

        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .extensions(extensionDtos)
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getName() : null)
                .locationId(user.getLocation() != null ? user.getLocation().getId() : null)
                .locationName(user.getLocation() != null ? user.getLocation().getName() : null)
                .locationSubRegion(user.getLocation() != null ? user.getLocation().getSubregion() : null)
                .locationExtCode(user.getLocation() != null ? user.getLocation().getExtCode() : null)
                .build();
    }

    private void syncExtensions(User user, List<ExtensionDto> extensionDtos) {
        user.getExtensions().clear();

        if (extensionDtos == null) {
            return;
        }

        for (ExtensionDto dto : extensionDtos) {
            if (dto.getType() == null || dto.getType().isBlank()
                    || dto.getNumber() == null || dto.getNumber().isBlank()) {
                continue;
            }

            Extension extension = Extension.builder()
                    .type(dto.getType().trim())
                    .number(dto.getNumber().trim())
                    .user(user)
                    .build();

            user.getExtensions().add(extension);
        }
    }

    private User mapToEntity(UserDto dto) {
        Company company = dto.getCompanyId() != null ? companyRepository.findById(dto.getCompanyId()).orElse(null)
                : null;

        Location location = dto.getLocationId() != null ? locationRepository.findById(dto.getLocationId()).orElse(null)
                : null;

        User user = User.builder()
                .id(dto.getId())
                .name(dto.getName())
                .email(dto.getEmail())
                .company(company)
                .location(location)
                .newEntity(true)
                .build();

        syncExtensions(user, dto.getExtensions());

        return user;
    }

    private Long findNextAvailableId() {
        List<Long> existingIds = userRepository.findAllIdsOrdered();
        long candidate = 1L;
        for (Long id : existingIds) {
            if (id == null) {
                continue;
            }
            if (id.longValue() == candidate) {
                candidate++;
            } else if (id > candidate) {
                break;
            }
        }
        return candidate;
    }

    // Helper method untuk memetakan nama field sort ke attribute JPA / alias query
    private Sort createSort(String sortField, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        if ("location".equalsIgnoreCase(sortField) || "locationName".equalsIgnoreCase(sortField)) {
            // Sort utama berdasarkan nama lokasi, kemudian nama user jika lokasi sama
            return Sort.by(direction, "l.name").and(Sort.by(Sort.Direction.ASC, "name"));
        } else if ("company".equalsIgnoreCase(sortField) || "companyName".equalsIgnoreCase(sortField)) {
            return Sort.by(direction, "c.name").and(Sort.by(Sort.Direction.ASC, "name"));
        } else if ("email".equalsIgnoreCase(sortField)) {
            return Sort.by(direction, "email");
        }

        // Default sort by nama user
        return Sort.by(direction, "name");
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        String email = (userDto.getEmail() != null && !userDto.getEmail().trim().isEmpty())
                ? userDto.getEmail().trim()
                : null;
        userDto.setEmail(email);

        String name = (userDto.getName() != null) ? userDto.getName().trim() : "";

        Company company = userDto.getCompanyId() != null
                ? companyRepository.findById(userDto.getCompanyId()).orElse(null)
                : null;

        Location location = userDto.getLocationId() != null
                ? locationRepository.findById(userDto.getLocationId()).orElse(null)
                : null;

        User existing = null;

        if (!name.isEmpty() && company != null && location != null && location.getSubregion() != null) {
            existing = userRepository.findExactDuplicate(
                    name,
                    company.getName(),
                    location.getName(),
                    location.getSubregion()).orElse(null);
        }

        if (existing != null) {
            List<ExtensionDto> mergedExtensions = new ArrayList<>();
            Set<String> existingKeys = new LinkedHashSet<>();

            if (existing.getExtensions() != null) {
                for (var ext : existing.getExtensions()) {
                    mergedExtensions.add(ExtensionDto.builder()
                            .type(ext.getType())
                            .number(ext.getNumber())
                            .build());
                    existingKeys.add(ext.getType().trim().toLowerCase() + "|" + ext.getNumber().trim());
                }
            }

            if (userDto.getExtensions() != null) {
                for (ExtensionDto ext : userDto.getExtensions()) {
                    if (ext.getNumber() != null && !ext.getNumber().trim().isEmpty()) {
                        String key = ext.getType().trim().toLowerCase() + "|" + ext.getNumber().trim();
                        if (existingKeys.add(key)) {
                            mergedExtensions.add(ext);
                        }
                    }
                }
            }

            userDto.setExtensions(mergedExtensions);

            if (userDto.getEmail() == null && existing.getEmail() != null) {
                userDto.setEmail(existing.getEmail());
            }

            return updateUser(existing.getId(), userDto);
        }

        User user = mapToEntity(userDto);
        user.setId(findNextAvailableId());
        return mapToDto(userRepository.save(user));
    }

    @Override
    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserDto> searchUsers(String keyword, String subregion) {
        return searchUsers(keyword, subregion, null, "location", "asc");
    }

    @Override
    public List<UserDto> searchUsers(String keyword, String subregion, String sortField, String sortDir) {
        return searchUsers(keyword, subregion, null, sortField, sortDir);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(String keyword, String subregion, Long locationId, String sortField, String sortDir) {
        return searchUsersPaginated(keyword, subregion, locationId, sortField, sortDir, 1, Integer.MAX_VALUE).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> searchUsersPaginated(String keyword, String subregion, Long locationId, String sortField, String sortDir, int page, int size) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanSubregion = (subregion != null && !subregion.trim().isEmpty()) ? subregion.trim() : null;

        int pageSize = size > 0 ? size : 15;
        int pageIndex = Math.max(0, page - 1);
        // Tie-breaker id agar urutan stabil antar halaman
        Sort sort = createSort(sortField, sortDir).and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        // Langkah 1: paginasi ID di database. Langkah 2: fetch detail user sekali jalan.
        Page<Long> idPage = userRepository.searchUserIds(cleanKeyword, cleanSubregion, locationId, pageable);
        List<Long> ids = idPage.getContent();

        Map<Long, User> usersById = ids.isEmpty()
                ? Map.of()
                : userRepository.findByIdIn(ids).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<UserDto> content = ids.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(this::mapToDto)
                .toList();

        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Override
    public UserDto updateUser(Long id, UserDto userDto) {
        return userRepository.findById(id)
                .map(existing -> {
                    existing.setName(userDto.getName());
                    existing.setEmail(userDto.getEmail());
                    syncExtensions(existing, userDto.getExtensions());

                    if (userDto.getCompanyId() != null) {
                        existing.setCompany(companyRepository.findById(userDto.getCompanyId()).orElse(null));
                    }

                    if (userDto.getLocationId() != null) {
                        existing.setLocation(locationRepository.findById(userDto.getLocationId()).orElse(null));
                    }

                    return mapToDto(userRepository.save(existing));
                })
                .orElse(null);
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
