import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AdminDashboard,
  AdminCoupon,
  AdminCategory,
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
  coupons: AdminCoupon[] = [];
  adminCategories: AdminCategory[] = [];
  activeSection: 'overview' | 'products' | 'orders' | 'coupons' | 'categories' = 'overview';
  editingCouponId: number | null = null;
  editingCategoryId: number | null = null;
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

  readonly couponForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]+$/)]],
    description: [''],
    discountType: ['PERCENTAGE' as 'PERCENTAGE' | 'FIXED', Validators.required],
    discountValue: [10, [Validators.required, Validators.min(0.01)]],
    minimumOrderValue: [0, [Validators.required, Validators.min(0)]],
    maximumDiscountValue: [0, Validators.min(0)],
    usageLimit: [0, Validators.min(0)],
    usageLimitPerUser: [1, [Validators.required, Validators.min(1)]],
    startsAt: [this.dateInput(new Date()), Validators.required],
    endsAt: [this.dateInput(new Date(Date.now() + 30 * 86_400_000)), Validators.required],
    active: [true]
  });

  readonly categoryForm = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    description: [''],
    active: [true]
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
    this.adminService.coupons().subscribe(value => this.coupons = value);
    this.adminService.categories().subscribe(value => this.adminCategories = value);
  }

  setSection(section: 'overview' | 'products' | 'orders' | 'coupons' | 'categories'): void {
    this.activeSection = section;
    this.clearMessages();
  }

  generateCategorySlug(): void {
    this.categoryForm.controls.slug.setValue(
      this.slug(this.categoryForm.controls.name.value)
    );
  }

  saveCategory(): void {
    this.clearMessages();
    if (this.categoryForm.invalid || this.isSubmitting) {
      this.categoryForm.markAllAsTouched();
      this.errorMessage = 'Revise os dados da categoria.';
      return;
    }
    const value = this.categoryForm.getRawValue();
    const request = {
      name: value.name.trim(),
      slug: value.slug.trim(),
      description: value.description.trim() || undefined,
      active: value.active
    };
    this.isSubmitting = true;
    const operation = this.editingCategoryId
      ? this.adminService.updateCategory(this.editingCategoryId, request)
      : this.adminService.createCategory(request);
    operation.subscribe({
      next: () => {
        this.message = this.editingCategoryId ? 'Categoria atualizada.' : 'Categoria criada.';
        this.isSubmitting = false;
        this.editingCategoryId = null;
        this.categoryForm.reset({ active: true });
        this.reloadCategories();
      },
      error: error => this.handleError(error)
    });
  }

  editCategory(category: AdminCategory): void {
    this.editingCategoryId = category.id;
    this.categoryForm.reset({
      name: category.name,
      slug: category.slug,
      description: category.description ?? '',
      active: category.active
    });
  }

  toggleCategory(category: AdminCategory): void {
    this.clearMessages();
    this.adminService.toggleCategory(category.id, !category.active).subscribe({
      next: updated => {
        this.adminCategories = this.adminCategories.map(item =>
          item.id === updated.id ? updated : item
        );
        this.reloadPublicCategories();
      },
      error: error => this.handleError(error)
    });
  }

  saveCoupon(): void {
    this.clearMessages();
    if (this.couponForm.invalid || this.isSubmitting) {
      this.couponForm.markAllAsTouched();
      this.errorMessage = 'Revise os dados do cupom.';
      return;
    }
    const value = this.couponForm.getRawValue();
    if (value.endsAt <= value.startsAt) {
      this.errorMessage = 'O término precisa ser posterior ao início.';
      return;
    }
    this.isSubmitting = true;
    const request = {
      code: value.code.trim().toUpperCase(),
      description: value.description.trim() || undefined,
      discountType: value.discountType,
      discountValue: value.discountValue,
      minimumOrderValue: value.minimumOrderValue,
      maximumDiscountValue: value.maximumDiscountValue || undefined,
      usageLimit: value.usageLimit || undefined,
      usageLimitPerUser: value.usageLimitPerUser,
      startsAt: value.startsAt,
      endsAt: value.endsAt,
      active: value.active
    };
    const operation = this.editingCouponId
      ? this.adminService.updateCoupon(this.editingCouponId, request)
      : this.adminService.createCoupon(request);
    operation.subscribe({
      next: () => {
        this.message = this.editingCouponId ? 'Cupom atualizado.' : 'Cupom criado.';
        this.isSubmitting = false;
        this.editingCouponId = null;
        this.resetCouponForm();
        this.adminService.coupons().subscribe(coupons => this.coupons = coupons);
      },
      error: error => this.handleError(error)
    });
  }

  editCoupon(coupon: AdminCoupon): void {
    this.editingCouponId = coupon.id;
    this.couponForm.reset({
      code: coupon.code,
      description: coupon.description ?? '',
      discountType: coupon.discountType,
      discountValue: coupon.discountValue,
      minimumOrderValue: coupon.minimumOrderValue,
      maximumDiscountValue: coupon.maximumDiscountValue ?? 0,
      usageLimit: coupon.usageLimit ?? 0,
      usageLimitPerUser: coupon.usageLimitPerUser,
      startsAt: coupon.startsAt.slice(0, 16),
      endsAt: coupon.endsAt.slice(0, 16),
      active: coupon.active
    });
  }

  toggleCoupon(coupon: AdminCoupon): void {
    this.adminService.toggleCoupon(coupon.id, !coupon.active).subscribe({
      next: updated => this.coupons = this.coupons.map(item =>
        item.id === updated.id ? updated : item
      ),
      error: error => this.handleError(error)
    });
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

  private resetCouponForm(): void {
    this.couponForm.reset({
      discountType: 'PERCENTAGE',
      discountValue: 10,
      minimumOrderValue: 0,
      maximumDiscountValue: 0,
      usageLimit: 0,
      usageLimitPerUser: 1,
      startsAt: this.dateInput(new Date()),
      endsAt: this.dateInput(new Date(Date.now() + 30 * 86_400_000)),
      active: true
    });
  }

  private reloadCategories(): void {
    this.adminService.categories().subscribe(value => this.adminCategories = value);
    this.reloadPublicCategories();
  }

  private reloadPublicCategories(): void {
    this.productService.refreshCategories().subscribe(categories => this.categories = categories);
  }

  private slug(value: string): string {
    return value.normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
  }

  private dateInput(date: Date): string {
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }
}
