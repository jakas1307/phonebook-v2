package com.pb.phonebook.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import com.pb.phonebook.dto.ImportResultDto;
import com.pb.phonebook.dto.LocationDto;
import com.pb.phonebook.dto.UserDto;
import com.pb.phonebook.repository.ExtensionRepository;
import com.pb.phonebook.repository.LocationRepository;
import com.pb.phonebook.service.CompanyService;
import com.pb.phonebook.service.LocationService;
import com.pb.phonebook.service.UserImportService;
import com.pb.phonebook.service.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CompanyService companyService;
    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final ExtensionRepository extensionRepository;
    private final UserImportService userImportService;

    private static final List<String> DEFAULT_EXTENSION_TYPES = List.of("IPPBX", "Cisco", "PABX", "Zoom");

    private List<String> resolveExtensionTypes() {
        List<String> types = extensionRepository.findDistinctTypes();
        return types.isEmpty() ? DEFAULT_EXTENSION_TYPES : types;
    }

    private List<String> allSubregions() {
        return locationService.getAllLocations()
                .stream()
                .map(LocationDto::getSubregion)
                .filter(sr -> sr != null && !sr.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    @GetMapping
    public String listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subregion,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "location") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            Model model,
            HttpServletRequest request) {

        // AJAX (fetch): kembalikan hanya fragment hasil, bukan halaman penuh.
        // Header "X-Requested-With: fetch" dikirim dari JS frontend; "HX-Request"
        // didukung juga kalau nanti memakai htmx.
        boolean fragmentRequest = "fetch".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || request.getHeader("HX-Request") != null;

        // Dropdown hanya dibutuhkan untuk halaman penuh, bukan fragment hasil.
        if (!fragmentRequest) {
            model.addAttribute("subregions", locationRepository.findDistinctSubregions());

            // Jika subregion dipilih, ambil lokasi sesuai subregion. Jika belum, ambil semua lokasi.
            List<LocationDto> locations = (subregion != null && !subregion.isBlank())
                    ? locationService.getLocationsBySubregion(subregion)
                    : locationService.getAllLocations();
            model.addAttribute("locations", locations);
        }

        model.addAttribute("selectedSubregion", subregion);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("keyword", keyword);

        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", "asc".equalsIgnoreCase(sortDir) ? "desc" : "asc");

        int pageSize = 15;
        Page<UserDto> userPage = userService.searchUsersPaginated(keyword, subregion, locationId, sortField, sortDir, page, pageSize);

        int totalPages = userPage.getTotalPages();
        int currentPage = Math.max(1, page);
        if (totalPages > 0 && currentPage > totalPages) {
            currentPage = totalPages;
        }

        long totalElements = userPage.getTotalElements();
        long startElement = totalElements == 0 ? 0 : ((long) (currentPage - 1) * pageSize) + 1;
        long endElement = Math.min((long) currentPage * pageSize, totalElements);

        int startPage = Math.max(1, currentPage - 2);
        int endPage = Math.min(totalPages == 0 ? 1 : totalPages, currentPage + 2);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("userPage", userPage);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("startElement", startElement);
        model.addAttribute("endElement", endElement);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        return fragmentRequest ? "users/list :: usersResults" : "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("user", new UserDto());
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("subregions", allSubregions());
        model.addAttribute("locations", List.of());
        model.addAttribute("extensionTypes", resolveExtensionTypes());

        return "users/create";
    }

    // Dipakai AJAX di form create/edit serta filter list user
    @ResponseBody
    @GetMapping("/locations-by-subregion")
    public List<LocationDto> getLocationBySubregion(@RequestParam(required = false) String subregion) {
        if (subregion != null && !subregion.isBlank()) {
            return locationService.getLocationsBySubregion(subregion);
        }
        return locationService.getAllLocations();
    }

    @PostMapping
    public String create(@ModelAttribute("user") UserDto dto,
            RedirectAttributes redirectAttributes) {
        userService.createUser(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Data user berhasil ditambahkan!");
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        UserDto dto = userService.getUserById(id);
        model.addAttribute("user", dto);
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("subregions", allSubregions());

        List<LocationDto> currentSubregionLocations = dto != null && dto.getLocationSubRegion() != null
                ? locationService.getLocationsBySubregion(dto.getLocationSubRegion())
                : List.of();
        model.addAttribute("locations", currentSubregionLocations);
        model.addAttribute("extensionTypes", resolveExtensionTypes());

        return "users/edit";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("user") UserDto dto,
            RedirectAttributes redirectAttributes) {
        userService.updateUser(id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Data User berhasil diperbaharui!");
        return "redirect:/users";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("successMessage", "Data User berhasil dihapus.");
        return "redirect:/users";
    }

    @GetMapping("/import")
    public String importForm() {
        return "users/import";
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = userImportService.generateTemplate();
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"phonebook_import_template.xlsx\"")
                .body(content);
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Pilih file Excel dulu.");
            return "redirect:/users/import";
        }

        try {
            ImportResultDto result = userImportService.importFromExcel(file.getInputStream());
            redirectAttributes.addFlashAttribute("importResult", result);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Gagal membaca file: " + e.getMessage());
            return "redirect:/users/import";
        }

        return "redirect:/users";
    }
}
