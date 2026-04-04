import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  private isLoggedInSubject = new BehaviorSubject<boolean>(false);

  currentUser$ = this.currentUserSubject.asObservable();
  isLoggedIn$ = this.isLoggedInSubject.asObservable();

  constructor() {
    this.checkAuthStatus();
  }

  login(email: string, password: string): Observable<User> {
    // TODO: Implement actual API call
    return new Observable(observer => {
      // Mock login for now
      setTimeout(() => {
        const mockUser: User = {
          id: 1,
          email: email,
          firstName: 'Maria',
          lastName: 'Silva',
          cpf: '123.456.789-00',
          active: true,
          roles: ['ROLE_CUSTOMER']
        };

        this.currentUserSubject.next(mockUser);
        this.isLoggedInSubject.next(true);
        localStorage.setItem('currentUser', JSON.stringify(mockUser));
        
        observer.next(mockUser);
        observer.complete();
      }, 1000);
    });
  }

  register(userData: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    cpf: string;
  }): Observable<User> {
    // TODO: Implement actual API call
    return new Observable(observer => {
      setTimeout(() => {
        const newUser: User = {
          id: Date.now(),
          email: userData.email,
          firstName: userData.firstName,
          lastName: userData.lastName,
          cpf: userData.cpf,
          active: true,
          roles: ['ROLE_CUSTOMER']
        };

        observer.next(newUser);
        observer.complete();
      }, 1000);
    });
  }

  logout(): void {
    this.currentUserSubject.next(null);
    this.isLoggedInSubject.next(false);
    localStorage.removeItem('currentUser');
    localStorage.removeItem('authToken');
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return this.isLoggedInSubject.value;
  }

  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return user ? user.roles.includes(role) : false;
  }

  private checkAuthStatus(): void {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      const user = JSON.parse(storedUser);
      this.currentUserSubject.next(user);
      this.isLoggedInSubject.next(true);
    }
  }
}
