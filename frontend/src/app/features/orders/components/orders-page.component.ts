import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Order } from '../../../core/models/order.model';
import { OrderService } from '../../../core/services/order.service';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './orders-page.component.html',
  styleUrls: ['./orders-page.component.scss']
})
export class OrdersPageComponent implements OnInit {
  private readonly orderService = inject(OrderService);
  orders: Order[] = [];
  isLoading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.orderService.list().subscribe({
      next: orders => {
        this.orders = orders;
        this.isLoading = false;
      },
      error: error => {
        this.errorMessage = error.error?.message ?? 'Não foi possível carregar seus pedidos.';
        this.isLoading = false;
      }
    });
  }

  statusLabel(status: string): string {
    return ({
      PENDING_PAYMENT: 'Aguardando pagamento',
      PAID: 'Pagamento aprovado',
      PROCESSING: 'Em preparação',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregue',
      CANCELLED: 'Cancelado'
    } as Record<string, string>)[status] ?? status;
  }
}
