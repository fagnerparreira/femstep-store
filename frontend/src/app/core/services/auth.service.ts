import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
  BehaviorSubject,
  catchError,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  tap
} from 'rxjs';
import { AuthResponse, RegisterRequest, UpdateProfileRequest, User } from '../models/user.model';
import { AuthTokenStore } from './auth-token-store.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = '/api/auth';
  private readonly currentUserSubject = new BehaviorSubject<User | null>(null);
  private readonly isLoggedInSubject = new BehaviorSubject(false);
  private restoreRequest$: Observable<User | null> | null = null;

  readonly currentUser$ = this.currentUserSubject.asObservable();
  readonly isLoggedIn$ = this.isLoggedInSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStore: AuthTokenStore
  ) {
    this.restoreSession().subscribe();
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, { email, password }, { withCredentials: true })
      .pipe(map(response => this.storeSession(response)));
  }

  register(userData: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, userData, { withCredentials: true })
      .pipe(map(response => this.storeSession(response)));
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.apiUrl}/forgot-password`,
      { email }
    );
  }

  resetPassword(token: string, password: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.apiUrl}/reset-password`,
      { token, password }
    );
  }

  verifyEmail(token: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.apiUrl}/verify-email`,
      { token }
    );
  }

  resendVerification(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.apiUrl}/resend-verification`,
      {}
    );
  }

  logout(): void {
    this.http
      .post<void>(`${this.apiUrl}/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => of(undefined)))
      .subscribe(() => this.clearSession());
  }

  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>('/api/account/profile', request).pipe(
      tap(user => {
        this.currentUserSubject.next({ ...user, active: true });
      })
    );
  }

  restoreSession(): Observable<User | null> {
    const currentUser = this.currentUserSubject.value;
    if (currentUser && this.tokenStore.get()) {
      return of(currentUser);
    }
    if (this.restoreRequest$) {
      return this.restoreRequest$;
    }

    this.restoreRequest$ = this.http
      .post<AuthResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        map(response => this.storeSession(response)),
        catchError(() => {
          this.clearSession();
          return of(null);
        }),
        finalize(() => {
          this.restoreRequest$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );

    return this.restoreRequest$;
  }

  ensureSession(): Observable<boolean> {
    if (this.isAuthenticated()) {
      return of(true);
    }
    return this.restoreSession().pipe(map(user => user !== null));
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return this.isLoggedInSubject.value && this.tokenStore.get() !== null;
  }

  hasRole(role: string): boolean {
    return this.currentUserSubject.value?.roles.includes(role) ?? false;
  }

  private storeSession(response: AuthResponse): User {
    const user = this.toUser(response);
    this.tokenStore.set(response.token);
    this.currentUserSubject.next(user);
    this.isLoggedInSubject.next(true);
    return user;
  }

  private clearSession(): void {
    this.tokenStore.clear();
    this.currentUserSubject.next(null);
    this.isLoggedInSubject.next(false);
    this.restoreRequest$ = null;
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
