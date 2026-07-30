import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CheckoutPreview,
  CheckoutRequest,
  Order,
  ShippingAddress
} from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly apiUrl = '/api';

  constructor(private readonly http: HttpClient) {}

  preview(address: ShippingAddress, couponCode?: string): Observable<CheckoutPreview> {
    return this.http.post<CheckoutPreview>(`${this.apiUrl}/checkout/preview`, {
      address,
      couponCode: couponCode || undefined
    });
  }

  checkout(request: CheckoutRequest): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/orders`, request);
  }

  list(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/orders`);
  }

  get(orderNumber: string): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/orders/${orderNumber}`);
  }

  sandboxApprove(orderNumber: string): Observable<Order> {
    return this.http.post<Order>(
      `${this.apiUrl}/orders/${orderNumber}/sandbox-approve`,
      {}
    );
  }

  cancel(orderNumber: string): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/orders/${orderNumber}/cancel`, {});
  }
}
