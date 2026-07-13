package com.bovae.yaj.tickets;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
class TicketController {

    private final TicketService ticketService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TicketResponse> list(@RequestParam(required = false) @Nullable UUID teamId) {
        return ticketService.list(teamId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
        TicketResponse created = ticketService.create(
                request.teamId(), request.type(), request.state(), request.epicId(), request.title(), request.body());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TicketResponse get(@PathVariable UUID id) {
        return ticketService.get(id);
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketUpdateRequest request) {
        return ticketService.update(
                id,
                request.teamId(),
                request.type(),
                request.state(),
                request.epicId(),
                request.title(),
                request.body());
    }

    @PatchMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TicketResponse changeState(@PathVariable UUID id, @Valid @RequestBody TicketStateChangeRequest request) {
        return ticketService.changeState(id, request.state());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ticketService.delete(id);
    }
}
