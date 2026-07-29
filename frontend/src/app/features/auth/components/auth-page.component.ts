import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-auth-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.component.html',
  styleUrls: ['./auth-page.component.scss']
})
export class AuthPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  mode: 'login' | 'register' = 'login';
  isSubmitting = false;
  errorMessage = '';

  readonly form = this.formBuilder.nonNullable.group({
    firstName: [''],
    lastName: [''],
    cpf: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]]
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.mode = this.route.snapshot.data['mode'] === 'register' ? 'register' : 'login';
    this.configureValidators();
  }

  submit(): void {
    this.errorMessage = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    const value = this.form.getRawValue();
    const request$ = this.mode === 'login'
      ? this.authService.login(value.email, value.password)
      : this.authService.register({
          email: value.email,
          password: value.password,
          firstName: value.firstName,
          lastName: value.lastName,
          cpf: value.cpf.replace(/\D/g, '') || undefined
        });

    request$.subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl?.startsWith('/') ? returnUrl : '/account');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.error?.message
          ?? (this.mode === 'login'
            ? 'E-mail ou senha inválidos.'
            : 'Não foi possível criar sua conta.');
        this.isSubmitting = false;
      }
    });
  }

  fieldInvalid(name: string): boolean {
    const control = this.form.get(name);
    return Boolean(control?.invalid && (control.dirty || control.touched));
  }

  private configureValidators(): void {
    if (this.mode === 'register') {
      this.form.controls.firstName.setValidators([Validators.required, Validators.minLength(2)]);
      this.form.controls.lastName.setValidators([Validators.required, Validators.minLength(2)]);
      this.form.controls.cpf.setValidators([Validators.pattern(/^\d{0}$|^\d{11}$/)]);
    }
    this.form.updateValueAndValidity();
  }
}
