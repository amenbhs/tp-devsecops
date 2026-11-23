package com.example.democyber;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final Counter helloCounter;

    public HelloController(MeterRegistry registry) {
        // Métrique personnalisée visible dans /actuator/prometheus (TP4)
        this.helloCounter = Counter.builder("democyber_hello_requests_total")
                .description("Nombre d'appels à la page d'accueil")
                .register(registry);
    }

    @GetMapping("/")
    public String hello() {
        helloCounter.increment();
        return "Bonjour depuis democyber Spring Boot !";
    }

    @GetMapping("/api/status")
    public Status status() {
        return new Status("UP", "democyber");
    }

    public record Status(String status, String application) {
    }
}
