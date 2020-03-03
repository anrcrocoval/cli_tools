library(tidyr)
library(dplyr)
library(ggplot2)
library(gtable)
library(grid)
library(gridExtra)

# prepare = function(data) {
#     data$i = as.factor(data$i)
#     data$method = as.factor(data$method)
#     data$n = as.factor(data$n)
#     data$model = as.factor(data$model)
# }

plot.coverage = function(data, fill_by, facet_by) {
    font_size = 20
    ymin = min(min(data$X.in), 50)
    p = ggplot(data = data, aes_string(x = "nearest", y = "X.in", color = fill_by)) +
    geom_point() +
    facet_grid(reformulate(facet_by, "n"), labeller = label_both) +
    coord_cartesian(ylim = c(ymin, 100)) +
    geom_hline(yintercept = 95, linetype="dotted") +
    scale_y_continuous(breaks = sort(unique(c(round(seq(ymin, 95, length.out = 4) / 5) * 5, 95)))) +
    theme_bw() +
    labs(x = "Distance to nearest fiducial point", y = "Coverage (%)") +
    guides(colour = guide_legend(override.aes = list(size=4))) +
    scale_colour_grey(name = paste(fill_by, ":", sep = ""), start = 0, end = 0.5) +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    return(p)
}

plot.coverage2 = function(data, fill_by) {
    font_size = 20
    ymin = min(min(data$X.in), 50)
    p = ggplot(data = data, aes_string(x = "nearest", y = "X.in", color = fill_by)) +
    geom_point() +
    facet_grid(reformulate(".", "n"), labeller = label_both) +
    coord_cartesian(ylim = c(ymin, 100)) +
    geom_hline(yintercept = 95, linetype="dotted") +
    scale_y_continuous(breaks = sort(unique(c(round(seq(ymin, 95, length.out = 4) / 5) * 5, 95)))) +
    theme_bw() +
    labs(x = "Distance to nearest fiducial point", y = "Coverage (%)") +
    guides(colour = guide_legend(override.aes = list(size=4))) +
    scale_colour_grey(name = paste(fill_by, ":", sep = ""), start = 0, end = 0.5) +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    return(p)
}

plot.area = function(data, fill_by, facet_by, log) {
    font_size = 20
    ymin = min(data$area.mean)
    ymax = max(data$area.mean)
    p = ggplot(data = data, aes_string(x = "nearest", y = "area.mean", color = fill_by)) +
    geom_point() +
    facet_grid(reformulate(facet_by, "n"), labeller = label_both) +
    theme_bw() +
    labs(x = "Distance to nearest fiducial point", y = "Mean area (a.u)") +
    scale_colour_grey(name = paste(fill_by, ":", sep = ""), start = 0, end = 0.5) +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    if(log) {
        p = p + scale_y_log10()
    }
    return(p)
}


plot.area2 = function(data, fill_by, log) {
    font_size = 20
    ymin = min(data$area.mean)
    ymax = max(data$area.mean)
    p = ggplot(data = data, aes_string(x = "nearest", y = "area.mean", color = fill_by)) +
    # geom_line(stat = "identity", show.legend = TRUE) +
    geom_point() +
    # geom_line() +
    facet_grid(reformulate(".", "n"), labeller = label_both) +
    # facet_grid(reformulate(facet_by, "n"), labeller = label_both) +
    # geom_errorbar(aes(ymin = area.mean - area.sd, ymax = area.mean + area.sd), width = 0.2, position = position_dodge(.9)) +
    theme_bw() +
    scale_colour_grey(name = paste(fill_by, ":", sep = ""), start = 0, end = 0.5) +
    labs(x = "Distance to nearest fiducial point", y = "Mean area (a.u)") +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    if(log) {
        p = p + scale_y_log10()
    }
    return(p)
}

plot.nearest = function(data) {
    font_size = 20
    ymin = min(data$nearest)
    ymax = max(data$nearest)
    p = ggplot(data = data, aes(x = i, y = nearest)) +
    geom_line(stat = "identity", show.legend = TRUE) +
    facet_wrap(~n) +
    theme_bw() +
    labs(x = "Distance to nearest fiducial point", y = "Nearest distance") +
    scale_fill_grey(name = "Model:") +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    return(p)
}

grid_arrange_shared_legend <- function(...,
           ncol = length(list(...)),
           nrow = 1,
           position = c("bottom", "right")
) {
    plots <- list(...)
    position <- match.arg(position)
    g <- ggplotGrob(plots[[1]] + theme(legend.position = position))$grobs
    legend <- g[[which(sapply(g, function(x) x$name) == "guide-box")]]
    lheight <- sum(legend$height)
    lwidth <- sum(legend$width)
    gl <- lapply(plots, function(x)
      x + theme(legend.position = "none"))
    gl <- c(gl, ncol = ncol, nrow = nrow)

    grob = do.call(arrangeGrob, c(gl))
    combined <- switch(
      position,
      "bottom" = arrangeGrob(
        grob,
        legend,
        ncol = 1,
        widths = unit(500, "mm"),
        heights = unit.c(unit(250, "mm") - lheight, lheight)
      )
    )
    return(combined)
}

grid_arrange_shared_legend2 <- function(...,
           ncol = length(list(...)),
           nrow = 1,
           position = c("bottom", "right")
) {
    plots <- list(...)
    position <- match.arg(position)
    gl <- lapply(plots, function(x)
      x + theme(legend.position = "none"))
    gl <- c(gl, ncol = ncol, nrow = nrow)

    grob = do.call(arrangeGrob, c(gl))
    return(grob)
}

# data_both_rigid_uniform = rbind(
#     read.csv("result_model_RIGID_transfo_RIGID_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n100_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )
# data_both_rigid_uniform$i = as.factor(data_both_rigid_uniform$i)
# data_both_rigid_uniform$n = as.factor(data_both_rigid_uniform$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_both_rigid_uniform, fill_by = "model", facet_by = "method"),
#         plot.area(data_both_rigid_uniform, fill_by = "model", facet_by = "method", TRUE)
#     ),
#     file = "plot_rigid_uniform.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )
# ggsave(plot.nearest(data_both_rigid_uniform), file = "plot_uniform_nearest.eps", dpi = 600, width = 500, height = 250, units = "mm")
#
# data_both_rigid_gaussian = rbind(
#     read.csv("result_model_RIGID_transfo_RIGID_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n100_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
# )
# data_both_rigid_gaussian$i = as.factor(data_both_rigid_gaussian$i)
# data_both_rigid_gaussian$n = as.factor(data_both_rigid_gaussian$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_both_rigid_gaussian, fill_by = "model", facet_by = "method"),
#         plot.area(data_both_rigid_gaussian, fill_by = "model", facet_by = "method", TRUE)
#     ),
#     file = "plot_rigid_gaussian.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )
# ggsave(plot.nearest(data_both_rigid_gaussian), file = "plot_gaussian_nearest.eps", dpi = 600, width = 500, height = 250, units = "mm")
#
#
#
# data_both_affine_uniform = rbind(
#     read.csv("result_model_RIGID_transfo_AFFINE_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n100_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )
# data_both_affine_uniform$i = as.factor(data_both_affine_uniform$i)
# data_both_affine_uniform$n = as.factor(data_both_affine_uniform$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_both_affine_uniform, fill_by = "model", facet_by = "method"),
#         plot.area(data_both_affine_uniform, fill_by = "model", facet_by = "method", TRUE)
#     ),
#     file = "plot_affine_uniform.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )
#
#
# data_both_affine_gaussian = rbind(
#     read.csv("result_model_RIGID_transfo_AFFINE_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n100_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
# )
# data_both_affine_gaussian$i = as.factor(data_both_affine_gaussian$i)
# data_both_affine_gaussian$n = as.factor(data_both_affine_gaussian$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_both_affine_gaussian, fill_by = "model", facet_by = "method"),
#         plot.area(data_both_affine_gaussian, fill_by = "model", facet_by = "method", TRUE)
#     ),
#     file = "plot_affine_gaussian.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )

# data_affine_affine_uniform = rbind(
#     read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )
data_affine_affine_gaussian = rbind(
    read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
)
# data_affine_affine_both = rbind(
#     data.frame(data_affine_affine_uniform, layout = "uniform"),
#     data.frame(data_affine_affine_gaussian, layout = "gathered")
# )
# data_affine_affine_uniform$layout = as.factor(data_affine_affine_uniform$layout)
# data_affine_affine_uniform$i = as.factor(data_affine_affine_uniform$i)
# data_affine_affine_uniform$n = as.factor(data_affine_affine_uniform$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage2(data_affine_affine_uniform, fill_by = "method"),
#         plot.area2(data_affine_affine_uniform, fill_by = "method", TRUE)
#     ),
#     file = "plot_affine_affine_uniform.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )

# data_affine_affine_gaussian$layout = as.factor(data_affine_affine_gaussian$layout)
data_affine_affine_gaussian$i = as.factor(data_affine_affine_gaussian$i)
data_affine_affine_gaussian$n = as.factor(data_affine_affine_gaussian$n)
ggsave(
    grid_arrange_shared_legend(
        plot.coverage2(data_affine_affine_gaussian, fill_by = "method"),
        plot.area2(data_affine_affine_gaussian, fill_by = "method", TRUE)
    ),
    file = "plot_affine_affine_gaussian.eps", dpi = 600, width = 500, height = 250, units = "mm"
)

#
# data_rigid_rigid_uniform = rbind(
#     read.csv("result_model_RIGID_transfo_RIGID_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )
# data_rigid_rigid_gaussian = rbind(
#     read.csv("result_model_RIGID_transfo_RIGID_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
# )
# data_rigid_rigid_both = rbind(
#     data.frame(data_rigid_rigid_uniform, layout = "uniform"),
#     data.frame(data_rigid_rigid_gaussian, layout = "gathered")
# )
# data_rigid_rigid_both$layout = as.factor(data_rigid_rigid_both$layout)
# data_rigid_rigid_both$i = as.factor(data_rigid_rigid_both$i)
# data_rigid_rigid_both$n = as.factor(data_rigid_rigid_both$n)
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_rigid_rigid_both, fill_by = "layout", facet_by = "method"),
#         plot.area(data_rigid_rigid_both, fill_by = "layout", facet_by = "method", TRUE)
#     ),
#     file = "plot_rigid_rigid_both.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )




# data_both_rigid_uniform = rbind(
#     read.csv("result_model_RIGID_transfo_RIGID_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_RIGID_n100_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_RIGID_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )
data_both_rigid_gaussian = rbind(
    read.csv("result_model_RIGID_transfo_RIGID_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_RIGID_transfo_RIGID_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_RIGID_transfo_RIGID_n100_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_RIGID_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_RIGID_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_RIGID_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
)
data_both_affine_gaussian = rbind(
    read.csv("result_model_RIGID_transfo_AFFINE_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_RIGID_transfo_AFFINE_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_RIGID_transfo_AFFINE_n100_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_gaussian.csv", stringsAsFactors = TRUE),
    read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_gaussian.csv", stringsAsFactors = TRUE)
)
data_both_both_gaussian = rbind(
    data.frame(data_both_rigid_gaussian, transformation = "rigid"),
    data.frame(data_both_affine_gaussian, transformation = "affine")
)
data_both_both_gaussian$i = as.factor(data_both_both_gaussian$i)
data_both_both_gaussian$n = as.factor(data_both_both_gaussian$n)
data_both_both_gaussian = data_both_both_gaussian %>% filter(method == "analytic")

plot.coverage.box = function(data) {
    font_size = 20
    ymin = min(min(data$X.in), 50)
    p = ggplot(data = data, aes_string(x = "model", y = "X.in")) +
    geom_boxplot() +
    stat_boxplot(geom = 'errorbar') +
    facet_grid(reformulate("transformation", "n"), labeller = label_both) +
    coord_cartesian(ylim = c(ymin, 100)) +
    geom_hline(yintercept = 95, linetype="dotted") +
    scale_y_continuous(breaks = sort(unique(c(round(seq(ymin, 95, length.out = 4) / 5) * 5, 95)))) +
    theme_bw() +
    labs(x = "Model", y = "Coverage (%)") +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    return(p)
}

plot.area.box = function(data, log) {
    font_size = 20
    p = ggplot(data = data, aes_string(x = "model", y = "area.mean")) +
    geom_boxplot() +
    stat_boxplot(geom = 'errorbar') +
    facet_grid(reformulate("transformation", "n"), labeller = label_both) +
    theme_bw() +
    labs(x = "Model", y = "Mean area (a.u)") +
    theme(axis.title = element_text(size = font_size), axis.text = element_text(size = font_size), strip.text = element_text(size = font_size), legend.title = element_text(size = font_size), legend.text = element_text(size = font_size))
    if(log) {
        p = p + scale_y_log10()
    }
    return(p)
}

ggsave(
    grid_arrange_shared_legend2(
        plot.coverage.box(data_both_both_gaussian)
    ),
    file = "plot_rigid_affine.eps", dpi = 600, width = 250, height = 250, units = "mm"
)

ggsave(
    grid_arrange_shared_legend2(
        plot.area.box(data_both_both_gaussian %>% filter(transformation == "rigid"), TRUE)
    ),
    file = "plot_rigid_affine_area.eps", dpi = 600, width = 250, height = 250, units = "mm"
)


# data_both_affine_uniform = rbind(
#     read.csv("result_model_RIGID_transfo_AFFINE_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_RIGID_transfo_AFFINE_n100_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n10_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n25_layout_uniform.csv", stringsAsFactors = TRUE),
#     read.csv("result_model_AFFINE_transfo_AFFINE_n100_layout_uniform.csv", stringsAsFactors = TRUE)
# )

# data_both_affine_both = rbind(
#     data.frame(data_both_affine_uniform, layout = "uniform"),
#     data.frame(data_both_affine_gaussian, layout = "gathered")
# )
# data_both_affine_both$i = as.factor(data_both_affine_both$i)
# data_both_affine_both$n = as.factor(data_both_affine_both$n)
# data_both_affine_both = data_both_affine_both %>% filter(method == " analytic")
# ggsave(
#     grid_arrange_shared_legend(
#         plot.coverage(data_both_affine_both, fill_by = "model", facet_by = "layout"),
#         plot.area(data_both_affine_both, fill_by = "model", facet_by = "layout", TRUE)
#     ),
#     file = "plot_affine.eps", dpi = 600, width = 500, height = 250, units = "mm"
# )
