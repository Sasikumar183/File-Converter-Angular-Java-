import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule, HttpClientModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  loginData = {
    username: '',
    password: '',
  };

  constructor(private http: HttpClient) {}

  onSubmit() {
    const servletUrl = 'http://localhost:8080/DemoAng/LoginServlet';
    this.http.post<{ id: number }>(servletUrl, this.loginData).subscribe( //sending data to login servlet
      (response) => {
        console.log('Login successful', response);
        alert('Login successful!');
        const userId = response.id;
        if (userId) {
          window.location.href = `/home?id=${userId}`;
        }
      },
      (error) => {
        console.error('Error during login', error);
        alert('Login failed! Please check your credentials.');
      }
    );
  }
}
