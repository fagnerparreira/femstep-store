import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-account-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './account-page.component.html',
  styleUrls: ['./account-page.component.scss']
})
export class AccountPageComponent {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  readonly user$ = this.authService.currentUser$;
  editing = false;
  isSubmitting = false;
  feedbackMessage = '';

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    cpf: ['', Validators.pattern(/^\d{0}$|^\d{11}$/)]
  });

  openProfile(): void {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return;
    }
    this.form.reset({
      firstName: user.firstName,
      lastName: user.lastName,
      cpf: user.cpf ?? ''
    });
    this.feedbackMessage = '';
    this.editing = true;
  }

  saveProfile(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.isSubmitting = true;
    this.authService.updateProfile({
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      cpf: value.cpf.replace(/\D/g, '')
    }).subscribe({
      next: () => {
        this.feedbackMessage = 'Perfil atualizado.';
        this.editing = false;
        this.isSubmitting = false;
      },
      error: error => {
        this.feedbackMessage = error.error?.message ?? 'Não foi possível atualizar o perfil.';
        this.isSubmitting = false;
      }
    });
  }
}
