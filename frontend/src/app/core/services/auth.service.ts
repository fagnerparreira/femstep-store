import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
import { AuthResponse, RegisterRequest, User } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiUrl = '/api/auth';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  private isLoggedInSubject = new BehaviorSubject<boolean>(false);

  currentUser$ = this.currentUserSubject.asObservable();
  isLoggedIn$ = this.isLoggedInSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    @Inject(PLATFORM_ID) private readonly platformId: object
  ) {
    this.checkAuthStatus();
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, { email, password })
      .pipe(
        tap(response => this.storeSession(response)),
        map(response => this.toUser(response))
      );
  }

  register(userData: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, userData)
      .pipe(
        tap(response => this.storeSession(response)),
        map(response => this.toUser(response))
      );
  }

  logout(): void {
    this.currentUserSubject.next(null);
    this.isLoggedInSubject.next(false);
    if (isPlatformBrowser(this.platformId)) {
      sessionStorage.removeItem('currentUser');
      sessionStorage.removeItem('authToken');
    }
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
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const storedUser = sessionStorage.getItem('currentUser');
    const authToken = sessionStorage.getItem('authToken');
    if (!storedUser || !authToken) {
      return;
    }

    try {
      const user = JSON.parse(storedUser) as User;
      this.currentUserSubject.next(user);
      this.isLoggedInSubject.next(true);
    } catch {
      this.logout();
    }
  }

  private storeSession(response: AuthResponse): void {
    const user = this.toUser(response);
    this.currentUserSubject.next(user);
    this.isLoggedInSubject.next(true);

    if (isPlatformBrowser(this.platformId)) {
      sessionStorage.setItem('authToken', response.token);
      sessionStorage.setItem('currentUser', JSON.stringify(user));
    }
  }

  private toUser(response: AuthResponse): User {
    return {
      id: response.userId,
      email: response.email,
      firstName: response.firstName,
      lastName: response.lastName,
      active: true,
      roles: response.roles
    };
  }
}
