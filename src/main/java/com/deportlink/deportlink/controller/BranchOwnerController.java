package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.service.BranchOwnerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@AllArgsConstructor
public class BranchOwnerController {
    private BranchOwnerService branchOwnerService;

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody @Valid BranchRequestDto branchDto){
        branchOwnerService.create(branchDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Sucursal creada con exito");
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable long id){
        branchOwnerService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Sucursal eliminada con exito"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable long id,
                                         @Valid @RequestBody BranchRequestDto branchDto){
        branchOwnerService.update(id, branchDto);
        return ResponseEntity.ok("Sucursal eliminada con exito");
    }

    @PatchMapping("/{idBranch}/activate")
    public ResponseEntity<Object> activate(@RequestParam long idBranch,
                                           @PathVariable long idClub){
        branchOwnerService.active(idBranch, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con exito"));
    }

    @PatchMapping("/{idBranch}/desactivate")
    public ResponseEntity<Object> desactivate(@RequestParam long idBranch,
                                              @PathVariable long idClub){
        branchOwnerService.desactive(idBranch, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con exito"));
    }

}
