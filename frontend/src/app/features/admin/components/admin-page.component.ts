import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AdminDashboard,
  AdminOrder,
  AdminProduct,
  AdminService
} from '../../../core/services/admin.service';
import { Category } from '../../../core/models/product.model';
import { ProductService } from '../../../core/services/product.service';

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss']
})
export class AdminPageComponent implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly productService = inject(ProductService);
  private readonly formBuilder = inject(FormBuilder);

  dashboard: AdminDashboard | null = null;
  products: AdminProduct[] = [];
  orders: AdminOrder[] = [];
  categories: Category[] = [];
  activeSection: 'overview' | 'products' | 'orders' = 'overview';
  expandedProductId: number | null = null;
  trackingCodes: Record<string, string> = {};
  isSubmitting = false;
  message = '';
  errorMessage = '';

  readonly productForm = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    description: ['', Validators.required],
    shortDescription: [''],
    price: [199.9, [Validators.required, Validators.min(0.01)]],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    featured: [false],
    material: ['Sintético premium'],
    brand: ['FemStep'],
    imageUrl: ['', Validators.required],
    sizes: ['34,35,36,37,38,39', Validators.required],
    colors: ['Preto', Validators.required],
    stock: [5, [Validators.required, Validators.min(0)]]
  });

  ngOnInit(): void {
    this.reload();
    this.productService.getCategories().subscribe(categories => {
      this.categories = categories;
      if (categories.length) {
        this.productForm.controls.categoryId.setValue(categories[0].id);
      }
    });
  }

  reload(): void {
    this.adminService.dashboard().subscribe(value => this.dashboard = value);
    this.adminService.products().subscribe(value => this.products = value);
    this.adminService.orders().subscribe(value => this.orders = value);
  }

  setSection(section: 'overview' | 'products' | 'orders'): void {
    this.activeSection = section;
    this.clearMessages();
  }

  generateSlug(): void {
    const slug = this.productForm.controls.name.value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
    this.productForm.controls.slug.setValue(slug);
  }

  createProduct(): void {
    this.clearMessages();
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      this.errorMessage = 'Revise os dados do novo produto.';
      return;
    }
    const value = this.productForm.getRawValue();
    const sizes = value.sizes.split(',').map(size => Number(size.trim())).filter(Number.isFinite);
    const colors = value.colors.split(',').map(color => color.trim()).filter(Boolean);
    const prefix = value.slug.replace(/-/g, '').slice(0, 8).toUpperCase();
    const variants = sizes.flatMap(size =>
      colors.map(color => ({
        sku: `${prefix}-${size}-${color.slice(0, 3).toUpperCase()}`,
        size,
        color,
        stock: value.stock
      }))
    );
    if (!variants.length) {
      this.errorMessage = 'Informe ao menos um tamanho e uma cor.';
      return;
    }
    this.isSubmitting = true;
    this.adminService.createProduct({
      name: value.name,
      slug: value.slug,
      description: value.description,
      shortDescription: value.shortDescription || undefined,
      price: value.price,
      categoryId: value.categoryId,
      featured: value.featured,
      material: value.material || undefined,
      brand: value.brand || undefined,
      imageUrl: value.imageUrl,
      variants
    }).subscribe({
      next: () => {
        this.message = 'Produto criado e publicado com sucesso.';
        this.isSubmitting = false;
        this.reload();
        this.productForm.reset({
          price: 199.9,
          categoryId: this.categories[0]?.id ?? 0,
          featured: false,
          material: 'Sintético premium',
          brand: 'FemStep',
          sizes: '34,35,36,37,38,39',
          colors: 'Preto',
          stock: 5
        });
      },
      error: error => this.handleError(error)
    });
  }

  toggleProduct(product: AdminProduct): void {
    this.adminService.toggleProduct(product.id, !product.active).subscribe({
      next: updated => {
        this.products = this.products.map(item => item.id === updated.id ? updated : item);
      },
      error: error => this.handleError(error)
    });
  }

  adjustStock(product: AdminProduct, variantId: number, stock: number): void {
    this.clearMessages();
    this.adminService.adjustStock(variantId, stock, 'Ajuste pelo painel').subscribe({
      next: () => {
        this.message = 'Estoque atualizado.';
        this.adminService.products().subscribe(products => {
          this.products = products;
          this.expandedProductId = product.id;
        });
      },
      error: error => this.handleError(error)
    });
  }

  nextStatus(status: string): string | null {
    return ({ PAID: 'PROCESSING', PROCESSING: 'SHIPPED', SHIPPED: 'DELIVERED' } as Record<string, string>)[status] ?? null;
  }

  advance(order: AdminOrder): void {
    const nextStatus = this.nextStatus(order.status);
    if (!nextStatus) {
      return;
    }
    this.clearMessages();
    this.adminService.advanceOrder(
      order.orderNumber,
      nextStatus,
      this.trackingCodes[order.orderNumber]
    ).subscribe({
      next: updated => {
        this.orders = this.orders.map(item =>
          item.orderNumber === updated.orderNumber ? updated : item
        );
        this.message = `Pedido ${updated.orderNumber} atualizado.`;
        this.adminService.dashboard().subscribe(value => this.dashboard = value);
      },
      error: error => this.handleError(error)
    });
  }

  actionLabel(status: string): string {
    return ({
      PAID: 'Iniciar preparação',
      PROCESSING: 'Marcar como enviado',
      SHIPPED: 'Marcar como entregue'
    } as Record<string, string>)[status] ?? '';
  }

  private clearMessages(): void {
    this.message = '';
    this.errorMessage = '';
  }

  private handleError(error: HttpErrorResponse): void {
    this.errorMessage = error.error?.message ?? 'Não foi possível concluir a operação.';
    this.isSubmitting = false;
  }
}
