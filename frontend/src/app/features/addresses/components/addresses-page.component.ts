import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Address, AddressRequest } from '../../../core/models/address.model';
import { AddressService } from '../../../core/services/address.service';
import { AuthService } from '../../../core/services/auth.service';
import { PostalCodeService } from '../../../core/services/postal-code.service';

@Component({
  selector: 'app-addresses-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './addresses-page.component.html',
  styleUrls: ['./addresses-page.component.scss']
})
export class AddressesPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly addressService = inject(AddressService);
  private readonly authService = inject(AuthService);
  private readonly postalCodeService = inject(PostalCodeService);

  addresses: Address[] = [];
  editingId: number | null = null;
  showForm = false;
  isSubmitting = false;
  errorMessage = '';
  postalCodeMessage = '';
  isLookingUpPostalCode = false;

  readonly form = this.formBuilder.nonNullable.group({
    label: ['Casa', Validators.required],
    recipientName: ['', Validators.required],
    postalCode: ['', [Validators.required, Validators.pattern(/^\d{8}$/)]],
    street: ['', Validators.required],
    number: ['', Validators.required],
    complement: [''],
    neighborhood: ['', Validators.required],
    city: ['', Validators.required],
    state: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    phone: ['', Validators.pattern(/^\d{0}$|^\d{10,11}$/)],
    defaultAddress: [false]
  });

  ngOnInit(): void {
    this.load();
  }

  openNew(): void {
    const user = this.authService.getCurrentUser();
    this.editingId = null;
    this.showForm = true;
    this.form.reset({
      label: 'Casa',
      recipientName: user ? `${user.firstName} ${user.lastName}` : '',
      postalCode: '',
      street: '',
      number: '',
      complement: '',
      neighborhood: '',
      city: '',
      state: '',
      phone: '',
      defaultAddress: !this.addresses.length
    });
  }

  edit(address: Address): void {
    this.editingId = address.id;
    this.showForm = true;
    this.form.reset({
      label: address.label,
      recipientName: address.recipientName,
      postalCode: address.postalCode,
      street: address.street,
      number: address.number,
      complement: address.complement ?? '',
      neighborhood: address.neighborhood,
      city: address.city,
      state: address.state,
      phone: address.phone ?? '',
      defaultAddress: address.defaultAddress
    });
  }

  save(): void {
    this.errorMessage = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Revise os campos do endereço.';
      return;
    }
    this.isSubmitting = true;
    const request = this.request();
    const operation = this.editingId
      ? this.addressService.update(this.editingId, request)
      : this.addressService.create(request);
    operation.subscribe({
      next: () => {
        this.isSubmitting = false;
        this.showForm = false;
        this.load();
      },
      error: error => this.handleError(error)
    });
  }

  lookupPostalCode(): void {
    const postalCode = this.form.controls.postalCode.value.replace(/\D/g, '');
    this.form.controls.postalCode.setValue(postalCode);
    if (postalCode.length !== 8 || this.isLookingUpPostalCode) {
      return;
    }

    this.isLookingUpPostalCode = true;
    this.postalCodeMessage = '';
    this.postalCodeService.lookup(postalCode).subscribe({
      next: address => {
        this.form.patchValue({
          street: address.street,
          neighborhood: address.neighborhood,
          city: address.city,
          state: address.state
        });
        this.postalCodeMessage = address.street
          ? 'Endereço preenchido pelo CEP. Confira o número.'
          : 'CEP localizado. Complete os campos que faltam.';
        this.isLookingUpPostalCode = false;
      },
      error: () => {
        this.postalCodeMessage = 'CEP não localizado automaticamente. Preencha o endereço manualmente.';
        this.isLookingUpPostalCode = false;
      }
    });
  }

  remove(address: Address): void {
    this.addressService.remove(address.id).subscribe({
      next: () => this.load(),
      error: error => this.handleError(error)
    });
  }

  makeDefault(address: Address): void {
    this.addressService.makeDefault(address.id).subscribe({
      next: () => this.load(),
      error: error => this.handleError(error)
    });
  }

  private load(): void {
    this.addressService.list().subscribe({
      next: addresses => this.addresses = addresses,
      error: error => this.handleError(error)
    });
  }

  private request(): AddressRequest {
    const value = this.form.getRawValue();
    return {
      label: value.label.trim(),
      recipientName: value.recipientName.trim(),
      postalCode: value.postalCode.replace(/\D/g, ''),
      street: value.street.trim(),
      number: value.number.trim(),
      complement: value.complement.trim() || undefined,
      neighborhood: value.neighborhood.trim(),
      city: value.city.trim(),
      state: value.state.toUpperCase(),
      phone: value.phone.replace(/\D/g, '') || undefined,
      defaultAddress: value.defaultAddress
    };
  }

  private handleError(error: HttpErrorResponse): void {
    this.errorMessage = error.error?.message ?? 'Não foi possível concluir a operação.';
    this.isSubmitting = false;
  }
}
