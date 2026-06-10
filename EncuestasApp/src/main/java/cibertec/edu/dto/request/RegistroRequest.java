package cibertec.edu.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistroRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // Getters y setters
    public String getNombre()                  { return nombre; }
    public void   setNombre(String nombre)     { this.nombre = nombre; }
    public String getEmail()                   { return email; }
    public void   setEmail(String email)       { this.email = email; }
    public String getPassword()                { return password; }
    public void   setPassword(String password) { this.password = password; }
}
