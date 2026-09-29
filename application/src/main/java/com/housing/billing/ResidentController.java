package com.housing.billing;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/residents")
public class ResidentController {

    private final ResidentRepository residentRepository;

    public ResidentController(ResidentRepository residentRepository) {
        this.residentRepository = residentRepository;
    }

    @GetMapping
    public List<Resident> getAllResidents() {
        return residentRepository.findAll();
    }

    @PostMapping
    public Resident addResident(@RequestBody Resident resident) {
        return residentRepository.save(resident);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResident(@PathVariable Long id) {
        residentRepository.deleteById(id);
    }
}