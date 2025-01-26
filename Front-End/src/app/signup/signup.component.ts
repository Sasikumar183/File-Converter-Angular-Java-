import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule, HttpErrorResponse} from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-signup',
  imports: [CommonModule, FormsModule,HttpClientModule, RouterLink],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.css'
})
export class SignupComponent {
  signupData = {
    username: '',
    password: '',
  };

  constructor(private http: HttpClient) {}

  onSubmit() {
    const servletUrl = 'http://localhost:8080/DemoAng/SignupServlet';
    this.http.post<{ id: number }>(servletUrl, this.signupData).subscribe( //sending data to servlet 
      (response) => {
        console.log('Signup successful', response);
        alert('Signup successful!');
        const userId = response.id;
        if (userId) {
          window.location.href = `/home?id=${userId}`;
        }
      },
      (error: HttpErrorResponse) => {
        if (error.status === 409) {
          // Handle username already exists case
          alert('Username already exists. Please choose a different username.');
        } else {
          console.error('Error during signup', error);
          alert('Signup failed!');
        }
      }
    );
  }
  

}
