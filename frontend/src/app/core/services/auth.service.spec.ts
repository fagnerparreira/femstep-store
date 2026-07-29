import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService account recovery', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/refresh').flush(
      { message: 'No session' },
      { status: 401, statusText: 'Unauthorized' }
    );
  });

  afterEach(() => http.verify());

  it('requests password recovery without exposing account state', () => {
    service.forgotPassword('cliente@example.test').subscribe(response => {
      expect(response.message).toContain('instruções');
    });

    const request = http.expectOne('/api/auth/forgot-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'cliente@example.test' });
    request.flush({
      message: 'Se o e-mail estiver cadastrado, você receberá as instruções.'
    });
  });

  it('sends the single-use token with the new password', () => {
    service.resetPassword('token-seguro', 'NovaSenhaSegura123!').subscribe();

    const request = http.expectOne('/api/auth/reset-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      token: 'token-seguro',
      password: 'NovaSenhaSegura123!'
    });
    request.flush({ message: 'Senha redefinida.' });
  });

  it('submits the email verification token', () => {
    service.verifyEmail('token-verificacao').subscribe();

    const request = http.expectOne('/api/auth/verify-email');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'token-verificacao' });
    request.flush({ message: 'E-mail verificado.' });
  });
});
