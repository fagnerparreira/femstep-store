import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable, throwError } from 'rxjs';

export interface PostalCodeAddress {
  postalCode: string;
  street: string;
  neighborhood: string;
  city: string;
  state: string;
}

interface ViaCepResponse {
  cep?: string;
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean | 'true';
}

@Injectable({ providedIn: 'root' })
export class PostalCodeService {
  constructor(private readonly http: HttpClient) {}

  lookup(value: string): Observable<PostalCodeAddress> {
    const postalCode = value.replace(/\D/g, '');
    if (!/^\d{8}$/.test(postalCode)) {
      return throwError(() => new Error('CEP inválido'));
    }

    return this.http
      .get<ViaCepResponse>(`https://viacep.com.br/ws/${postalCode}/json/`)
      .pipe(
        map(response => {
          if (response.erro === true || response.erro === 'true') {
            throw new Error('CEP não encontrado');
          }
          return {
            postalCode,
            street: response.logradouro?.trim() ?? '',
            neighborhood: response.bairro?.trim() ?? '',
            city: response.localidade?.trim() ?? '',
            state: response.uf?.trim().toUpperCase() ?? ''
          };
        })
      );
  }
}
