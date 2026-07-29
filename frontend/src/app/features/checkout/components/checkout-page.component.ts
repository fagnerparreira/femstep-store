import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CheckoutPreview, ShippingAddress } from '../../../core/models/order.model';
import { AuthService } from '../../../core/services/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { OrderService } from '../../../core/services/order.service';

@Component({
  selector: 'app-checkout-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './checkout-page.component.html',
  styleUrls: ['./checkout-page.component.scss']
})
export class CheckoutPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly orderService = inject(OrderService);
  private readonly cartService = inject(CartService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly cart$ = this.cartService.cart$;

  preview: CheckoutPreview | null = null;
  errorMessage = '';
  isLoadingPreview = false;
  isSubmitting = false;
  private readonly idempotencyKey = this.createIdempotencyKey();

  readonly form = this.formBuilder.nonNullable.group({
    recipientName: ['', [Validators.required, Validators.maxLength(255)]],
    postalCode: ['', [Validators.required, Validators.pattern(/^\d{8}$/)]],
    street: ['', [Validators.required, Validators.maxLength(255)]],
    number: ['', [Validators.required, Validators.maxLength(40)]],
    complement: ['', [Validators.maxLength(255)]],
    neighborhood: ['', [Validators.required, Validators.maxLength(160)]],
    city: ['', [Validators.required, Validators.maxLength(160)]],
    state: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    couponCode: [''],
    paymentMethod: ['PIX' as 'PIX' | 'SANDBOX_CARD', Validators.required],
    notes: ['']
  });

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (user) {
      this.form.controls.recipientName.setValue(`${user.firstName} ${user.lastName}`);
    }
    this.cartService.refresh().subscribe({
      next: cart => {
        if (!cart.items.length) {
          void this.router.navigate(['/cart']);
        }
      }
    });
  }

  calculate(): void {
    this.errorMessage = '';
    this.markAddressTouched();
    if (!this.addressValid()) {
      this.errorMessage = 'Preencha corretamente o endereço para calcular a entrega.';
      return;
    }
    this.isLoadingPreview = true;
    this.orderService.preview(this.address(), this.form.controls.couponCode.value.trim())
      .subscribe({
        next: preview => {
          this.preview = preview;
          this.isLoadingPreview = false;
        },
        error: error => {
          this.errorMessage = this.apiMessage(error);
          this.isLoadingPreview = false;
        }
      });
  }

  submit(): void {
    this.errorMessage = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Revise os campos destacados antes de continuar.';
      return;
    }
    this.isSubmitting = true;
    const value = this.form.getRawValue();
    this.orderService.checkout({
      address: this.address(),
      couponCode: value.couponCode.trim() || undefined,
      paymentMethod: value.paymentMethod,
      idempotencyKey: this.idempotencyKey,
      notes: value.notes.trim() || undefined
    }).subscribe({
      next: order => {
        this.cartService.refresh().subscribe();
        void this.router.navigate(['/account/orders', order.orderNumber]);
      },
      error: error => {
        this.errorMessage = this.apiMessage(error);
        this.isSubmitting = false;
      }
    });
  }

  invalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  private address(): ShippingAddress {
    const value = this.form.getRawValue();
    return {
      recipientName: value.recipientName.trim(),
      postalCode: value.postalCode.replace(/\D/g, ''),
      street: value.street.trim(),
      number: value.number.trim(),
      complement: value.complement.trim() || undefined,
      neighborhood: value.neighborhood.trim(),
      city: value.city.trim(),
      state: value.state.trim().toUpperCase()
    };
  }

  private addressValid(): boolean {
    return [
      'recipientName',
      'postalCode',
      'street',
      'number',
      'neighborhood',
      'city',
      'state'
    ].every(name => this.form.get(name)?.valid);
  }

  private markAddressTouched(): void {
    [
      'recipientName',
      'postalCode',
      'street',
      'number',
      'neighborhood',
      'city',
      'state'
    ].forEach(name => this.form.get(name)?.markAsTouched());
  }

  private createIdempotencyKey(): string {
    const random = globalThis.crypto?.randomUUID?.()
      ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    return `web-${random}`;
  }

  private apiMessage(error: HttpErrorResponse): string {
    return error.error?.message ?? 'Não foi possível concluir esta etapa. Tente novamente.';
  }
}
