import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { User } from '../../../core/models/user.model';
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

  mode: 'login' | 'register' | 'forgot' | 'reset' | 'verify' = 'login';
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';
  token = '';

  readonly form = this.formBuilder.nonNullable.group({
    firstName: [''],
    lastName: [''],
    cpf: [''],
    email: [''],
    password: [''],
    confirmPassword: ['']
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    const configuredMode = this.route.snapshot.data['mode'];
    this.mode = ['register', 'forgot', 'reset', 'verify'].includes(configuredMode)
      ? configuredMode
      : 'login';
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    this.configureValidators();
    if (this.mode === 'verify') {
      this.verifyEmail();
    }
  }

  submit(): void {
    this.errorMessage = '';
    this.successMessage = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.mode === 'reset'
        && this.form.controls.password.value !== this.form.controls.confirmPassword.value) {
      this.errorMessage = 'As senhas não coincidem.';
      return;
    }

    this.isSubmitting = true;
    const value = this.form.getRawValue();
    const request$: Observable<User | { message: string }> = this.mode === 'login'
      ? this.authService.login(value.email, value.password)
      : this.mode === 'register'
        ? this.authService.register({
            email: value.email,
            password: value.password,
            firstName: value.firstName,
            lastName: value.lastName,
            cpf: value.cpf.replace(/\D/g, '') || undefined
          })
        : this.mode === 'forgot'
          ? this.authService.forgotPassword(value.email)
          : this.authService.resetPassword(this.token, value.password);

    request$.subscribe({
      next: result => {
        this.isSubmitting = false;
        if (this.mode === 'login' || this.mode === 'register') {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          void this.router.navigateByUrl(returnUrl?.startsWith('/') ? returnUrl : '/account');
          return;
        }
        this.successMessage = 'message' in result
          ? result.message
          : 'Operação concluída.';
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.error?.message ?? 'Não foi possível concluir esta solicitação.';
        this.isSubmitting = false;
      }
    });
  }

  fieldInvalid(name: string): boolean {
    const control = this.form.get(name);
    return Boolean(control?.invalid && (control.dirty || control.touched));
  }

  get eyebrow(): string {
    return {
      login: 'Boas-vindas de volta',
      register: 'Crie sua conta',
      forgot: 'Recuperação segura',
      reset: 'Nova senha',
      verify: 'Confirmação de e-mail'
    }[this.mode];
  }

  get title(): string {
    return {
      login: 'Entre na MOVAZU',
      register: 'Vamos caminhar juntas',
      forgot: 'Recupere seu acesso',
      reset: 'Defina uma nova senha',
      verify: 'Verificando seu e-mail'
    }[this.mode];
  }

  get intro(): string {
    return {
      login: 'Acesse seus pedidos, endereços e uma sacola sincronizada.',
      register: 'Cadastre-se para finalizar compras e acompanhar cada pedido.',
      forgot: 'Informe seu e-mail. Se houver uma conta, enviaremos as instruções.',
      reset: 'Use uma senha nova com pelo menos 12 caracteres.',
      verify: 'Aguarde enquanto validamos o link de uso único.'
    }[this.mode];
  }

  get submitLabel(): string {
    return {
      login: 'Entrar',
      register: 'Criar minha conta',
      forgot: 'Enviar instruções',
      reset: 'Redefinir senha',
      verify: 'Verificando...'
    }[this.mode];
  }

  private configureValidators(): void {
    Object.values(this.form.controls).forEach(control => control.clearValidators());
    if (this.mode === 'login' || this.mode === 'register' || this.mode === 'forgot') {
      this.form.controls.email.setValidators([Validators.required, Validators.email]);
    }
    if (this.mode === 'login' || this.mode === 'register' || this.mode === 'reset') {
      this.form.controls.password.setValidators([
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128)
      ]);
    }
    if (this.mode === 'register') {
      this.form.controls.firstName.setValidators([Validators.required, Validators.minLength(2)]);
      this.form.controls.lastName.setValidators([Validators.required, Validators.minLength(2)]);
      this.form.controls.cpf.setValidators([Validators.pattern(/^\d{0}$|^\d{11}$/)]);
    }
    if (this.mode === 'reset') {
      this.form.controls.confirmPassword.setValidators([
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128)
      ]);
      if (!this.token) {
        this.errorMessage = 'Link de redefinição inválido.';
      }
    }
    this.form.updateValueAndValidity();
  }

  private verifyEmail(): void {
    if (!this.token) {
      this.errorMessage = 'Link de verificação inválido.';
      return;
    }
    this.isSubmitting = true;
    this.authService.verifyEmail(this.token).subscribe({
      next: result => {
        this.successMessage = result.message;
        this.isSubmitting = false;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.error?.message ?? 'O link é inválido ou expirou.';
        this.isSubmitting = false;
      }
    });
  }
}
