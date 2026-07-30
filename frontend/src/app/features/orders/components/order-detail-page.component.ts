import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Order } from '../../../core/models/order.model';
import { OrderService } from '../../../core/services/order.service';

@Component({
  selector: 'app-order-detail-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './order-detail-page.component.html',
  styleUrls: ['./order-detail-page.component.scss']
})
export class OrderDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  order: Order | null = null;
  isLoading = true;
  isApproving = false;
  isCancelling = false;
  copied = false;
  errorMessage = '';

  ngOnInit(): void {
    const orderNumber = this.route.snapshot.paramMap.get('orderNumber') ?? '';
    this.orderService.get(orderNumber).subscribe({
      next: order => {
        this.order = order;
        this.isLoading = false;
      },
      error: error => {
        this.errorMessage = error.error?.message ?? 'Pedido não encontrado.';
        this.isLoading = false;
      }
    });
  }

  approveSandbox(): void {
    if (!this.order) {
      return;
    }
    this.isApproving = true;
    this.orderService.sandboxApprove(this.order.orderNumber).subscribe({
      next: order => {
        this.order = order;
        this.isApproving = false;
      },
      error: error => {
        this.errorMessage = error.error?.message ?? 'Não foi possível aprovar o pagamento.';
        this.isApproving = false;
      }
    });
  }

  cancel(): void {
    if (!this.order) {
      return;
    }
    this.isCancelling = true;
    this.orderService.cancel(this.order.orderNumber).subscribe({
      next: order => {
        this.order = order;
        this.isCancelling = false;
      },
      error: error => {
        this.errorMessage = error.error?.message ?? 'Não foi possível cancelar o pedido.';
        this.isCancelling = false;
      }
    });
  }

  copyPix(): void {
    if (!this.order?.pixCode) {
      return;
    }
    navigator.clipboard.writeText(this.order.pixCode).then(() => {
      this.copied = true;
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
