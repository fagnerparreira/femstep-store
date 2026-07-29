import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Address, AddressRequest } from '../models/address.model';

@Injectable({ providedIn: 'root' })
export class AddressService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/account/addresses';

  list(): Observable<Address[]> {
    return this.http.get<Address[]>(this.apiUrl);
  }

  create(request: AddressRequest): Observable<Address> {
    return this.http.post<Address>(this.apiUrl, request);
  }

  update(addressId: number, request: AddressRequest): Observable<Address> {
    return this.http.put<Address>(`${this.apiUrl}/${addressId}`, request);
  }

  remove(addressId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${addressId}`);
  }

  makeDefault(addressId: number): Observable<Address> {
    return this.http.patch<Address>(`${this.apiUrl}/${addressId}/default`, {});
  }
}
